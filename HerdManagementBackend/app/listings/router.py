"""Moduł publikacji ogłoszeń sprzedażowych.

Realizowane wymagania:

* F12 — ogłoszenie powstaje jako szkic (``draft``) i staje się widoczne dopiero
  po wywołaniu ``POST /listings/{id}/publish``.
* F13 — ``GET /animals/{animal_id}/listing-draft`` zwraca dane karty zwierzęcia
  do wstępnego wypełnienia formularza.
* F14 — ``POST /listings/{id}/verify`` porównuje kartę zwierzęcia z rejestrem
  IRZplus i nadaje lub odbiera odznakę „dane potwierdzone w IRZplus”.

Zapytania kupujących (``/listings/{id}/inquiries``) nie wymagają uwierzytelnienia
i trafiają do aplikacji mobilnej przez synchronizację.
"""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app import crud, schemas
from app.database import get_db
from app.irzplus import router as irzplus_module
from app.listings.verification import VerificationResult, verify_animal
from app.models import ListingStatus

router = APIRouter(tags=["listings"])


def _listing_or_404(db: Session, listing_id: int):
    listing = crud.get_listing(db, listing_id)
    if listing is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Nie znaleziono ogłoszenia.")
    return listing


@router.get("/listings", response_model=list[schemas.ListingRead])
def list_listings(
    status_filter: ListingStatus | None = None,
    animal_id: int | None = None,
    db: Session = Depends(get_db),
):
    return crud.list_listings(db, status=status_filter, animal_id=animal_id)


@router.get("/listings/{listing_id}", response_model=schemas.ListingRead)
def get_listing(listing_id: int, db: Session = Depends(get_db)):
    return _listing_or_404(db, listing_id)


@router.get("/animals/{animal_id}/listing-draft", response_model=schemas.ListingDraft)
def listing_draft(animal_id: int, db: Session = Depends(get_db)):
    """Zwraca dane karty zwierzęcia do wstępnego wypełnienia formularza (wymaganie F13)."""
    animal = crud.get_animal(db, animal_id)
    if animal is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Nie znaleziono zwierzęcia.")

    existing = crud.get_active_listing_for_animal(db, animal_id)
    return schemas.ListingDraft(
        animal_id=animal.id,
        ear_tag=animal.ear_tag,
        name=animal.name,
        birth_date=animal.birth_date,
        breed=animal.breed,
        sex=animal.sex,
        herd_id=animal.herd_id,
        suggested_description=_describe(animal),
        existing_listing_id=existing.id if existing else None,
    )


@router.post("/listings", response_model=schemas.ListingRead, status_code=status.HTTP_201_CREATED)
def create_listing(payload: schemas.ListingCreate, db: Session = Depends(get_db)):
    if crud.get_animal(db, payload.animal_id) is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Nie znaleziono zwierzęcia.")
    if crud.get_active_listing_for_animal(db, payload.animal_id) is not None:
        raise HTTPException(
            status.HTTP_409_CONFLICT,
            detail="To zwierzę ma już aktywne ogłoszenie. Wycofaj je albo edytuj istniejące.",
        )
    return crud.create_listing(db, payload)


@router.put("/listings/{listing_id}", response_model=schemas.ListingRead)
def update_listing(
    listing_id: int, payload: schemas.ListingUpdate, db: Session = Depends(get_db)
):
    listing = _listing_or_404(db, listing_id)
    if listing.status == ListingStatus.SOLD:
        raise HTTPException(
            status.HTTP_409_CONFLICT, detail="Sprzedanego ogłoszenia nie można już edytować."
        )
    return crud.update_listing(db, listing, payload)


@router.post("/listings/{listing_id}/publish", response_model=schemas.ListingRead)
def publish_listing(listing_id: int, db: Session = Depends(get_db)):
    """Publikuje ogłoszenie (wymagania F12 i F14).

    Publikacja wymaga pozytywnej weryfikacji w rejestrze IRZplus, wykonywanej ponownie
    w chwili publikacji zamiast polegania na zapisanej odznace. Zapobiega to publikacji
    ogłoszenia zwierzęcia, które w międzyczasie zostało rozliczone w rejestrze.
    """
    listing = _listing_or_404(db, listing_id)
    if listing.status == ListingStatus.PUBLISHED:
        return listing
    if listing.status == ListingStatus.SOLD:
        raise HTTPException(
            status.HTTP_409_CONFLICT, detail="Ogłoszenie jest już rozliczone jako sprzedane."
        )

    result = _run_verification(db, listing)
    if not result.verified:
        raise HTTPException(
            status.HTTP_409_CONFLICT,
            detail="Nie można opublikować ogłoszenia bez potwierdzenia danych w IRZplus. "
            + (result.message or ""),
        )

    return crud.set_listing_status(db, listing, ListingStatus.PUBLISHED)


@router.post("/listings/{listing_id}/withdraw", response_model=schemas.ListingRead)
def withdraw_listing(listing_id: int, db: Session = Depends(get_db)):
    return crud.set_listing_status(db, _listing_or_404(db, listing_id), ListingStatus.WITHDRAWN)


@router.post("/listings/{listing_id}/sold", response_model=schemas.ListingRead)
def mark_sold(listing_id: int, db: Session = Depends(get_db)):
    """Oznacza ogłoszenie jako sprzedane.

    Zmienia wyłącznie status ogłoszenia. Ubycie zwierzęcia jest odrębnym zdarzeniem,
    wymagającym zgłoszenia do IRZplus w terminie 7 dni.
    """
    return crud.set_listing_status(db, _listing_or_404(db, listing_id), ListingStatus.SOLD)


@router.post("/listings/{listing_id}/verify", response_model=schemas.ListingVerification)
def verify_listing(listing_id: int, db: Session = Depends(get_db)):
    """Porównuje dane zwierzęcia z rejestrem IRZplus i ustawia odznakę (wymaganie F14)."""
    listing = _listing_or_404(db, listing_id)
    result = _run_verification(db, listing)

    return schemas.ListingVerification(
        listing_id=listing.id,
        irz_verified=listing.irz_verified,
        irz_verified_at=listing.irz_verified_at,
        message=listing.irz_message,
        discrepancies=result.discrepancies,
    )


def _run_verification(db: Session, listing) -> VerificationResult:
    """Weryfikuje ogłoszenie w rejestrze i zapisuje wynik; wspólne dla ``/verify`` i ``/publish``."""
    animal = crud.get_animal(db, listing.animal_id)
    if animal is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Nie znaleziono zwierzęcia.")

    store = irzplus_module.store
    result = verify_animal(
        animal=animal,
        registry=store.get_animal(animal.ear_tag),
        closed=store.is_closed(animal.ear_tag),
    )
    crud.save_listing_verification(db, listing, result.verified, result.message)
    return result


@router.delete("/listings/{listing_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_listing(listing_id: int, db: Session = Depends(get_db)):
    if not crud.delete_listing(db, listing_id):
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Nie znaleziono ogłoszenia.")


@router.get("/listings/{listing_id}/inquiries", response_model=list[schemas.InquiryRead])
def list_inquiries(listing_id: int, db: Session = Depends(get_db)):
    _listing_or_404(db, listing_id)
    return crud.list_inquiries(db, listing_id)


@router.post(
    "/listings/{listing_id}/inquiries",
    response_model=schemas.InquiryRead,
    status_code=status.HTTP_201_CREATED,
)
def create_inquiry(
    listing_id: int, payload: schemas.InquiryCreate, db: Session = Depends(get_db)
):
    listing = _listing_or_404(db, listing_id)
    if listing.status != ListingStatus.PUBLISHED:
        raise HTTPException(
            status.HTTP_409_CONFLICT,
            detail="Do tego ogłoszenia nie można wysłać zapytania — nie jest opublikowane.",
        )
    return crud.create_inquiry(db, listing_id, payload)


def _describe(animal) -> str:
    """Tworzy proponowany opis ogłoszenia na podstawie karty zwierzęcia.

    Wielka litera jest ustawiana wyłącznie dla pierwszego znaku, ponieważ
    ``str.capitalize()`` zmieniłoby wielkość liter numeru kolczyka.
    """
    parts: list[str] = []
    if animal.breed:
        parts.append(animal.breed)
    if animal.birth_date:
        parts.append(f"ur. {animal.birth_date.isoformat()}")
    parts.append(f"kolczyk {animal.ear_tag}")

    text = ", ".join(parts)
    return text[:1].upper() + text[1:] + "."
