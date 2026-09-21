"""Moduł publikacji ogłoszeń sprzedażowych — zadanie 9.

Trzy wymagania z dokumentacji sterują kształtem tego API:

* **F12** — publikacja nie dzieje się sama. Ogłoszenie powstaje jako szkic
  (``draft``) i dopiero osobne wywołanie ``POST /listings/{id}/publish``
  czyni je widocznym. Ten sam wzorzec co przy zgłoszeniach do IRZplus:
  zapis przygotowuje aplikacja, decyzję podejmuje człowiek.
* **F13** — ``GET /listings/draft-for/{animal_id}`` zwraca dane karty zwierzęcia
  gotowe do wstawienia w formularz, żeby hodowca nie przepisywał ich ręcznie.
* **F14** — ``POST /listings/{id}/verify`` porównuje kartę zwierzęcia z rejestrem
  IRZplus i nadaje (albo odbiera) odznakę „dane potwierdzone w IRZplus”.

Zapytania kupujących (``/listings/{id}/inquiries``) są nieuwierzytelnione —
w prototypie giełda jest otwarta, a po stronie hodowcy liczy się to, że zapytanie
dotrze do telefonu przez synchronizację pobierającą.
"""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app import crud, schemas
from app.database import get_db
from app.irzplus import router as irzplus_module
from app.listings.verification import verify_animal
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
    """Dane karty zwierzęcia do wstępnego wypełnienia formularza (F13)."""
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
    """Jawna publikacja ogłoszenia — wymaganie F12.

    Świadomie nie wymagamy tu odznaki IRZplus: hodowca ma prawo wystawić zwierzę,
    którego dane jeszcze nie przeszły weryfikacji. Brak odznaki jest widoczny dla
    kupującego i to on ocenia ryzyko — blokada byłaby nadmiarowa wobec F14.
    """
    listing = _listing_or_404(db, listing_id)
    if listing.status == ListingStatus.PUBLISHED:
        return listing
    if listing.status == ListingStatus.SOLD:
        raise HTTPException(
            status.HTTP_409_CONFLICT, detail="Ogłoszenie jest już rozliczone jako sprzedane."
        )
    return crud.set_listing_status(db, listing, ListingStatus.PUBLISHED)


@router.post("/listings/{listing_id}/withdraw", response_model=schemas.ListingRead)
def withdraw_listing(listing_id: int, db: Session = Depends(get_db)):
    return crud.set_listing_status(db, _listing_or_404(db, listing_id), ListingStatus.WITHDRAWN)


@router.post("/listings/{listing_id}/sold", response_model=schemas.ListingRead)
def mark_sold(listing_id: int, db: Session = Depends(get_db)):
    """Oznacza sprzedaż ogłoszenia.

    To wyłącznie stan giełdy. Ubycie zwierzęcia z gospodarstwa jest osobnym
    zdarzeniem i wymaga zgłoszenia do IRZplus w ciągu 7 dni — aplikacja
    przypomina o tym na karcie zwierzęcia.
    """
    return crud.set_listing_status(db, _listing_or_404(db, listing_id), ListingStatus.SOLD)


@router.post("/listings/{listing_id}/verify", response_model=schemas.ListingVerification)
def verify_listing(listing_id: int, db: Session = Depends(get_db)):
    """Porównuje dane zwierzęcia z rejestrem IRZplus i ustawia odznakę (F14)."""
    listing = _listing_or_404(db, listing_id)
    animal = crud.get_animal(db, listing.animal_id)
    if animal is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Nie znaleziono zwierzęcia.")

    store = irzplus_module.store
    result = verify_animal(
        animal=animal,
        registry=store.get_animal(animal.ear_tag),
        closed=store.is_closed(animal.ear_tag),
    )
    saved = crud.save_listing_verification(db, listing, result.verified, result.message)

    return schemas.ListingVerification(
        listing_id=saved.id,
        irz_verified=saved.irz_verified,
        irz_verified_at=saved.irz_verified_at,
        message=saved.irz_message,
        discrepancies=result.discrepancies,
    )


@router.delete("/listings/{listing_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_listing(listing_id: int, db: Session = Depends(get_db)):
    if not crud.delete_listing(db, listing_id):
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Nie znaleziono ogłoszenia.")


# --- Zapytania kupujących -----------------------------------------------

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
    """Szkic opisu z danych karty — hodowca może go nadpisać.

    Wielką literę ustawiamy tylko na pierwszym znaku: ``str.capitalize()``
    obniżyłoby wielkość liter w całym napisie i zepsuło numer kolczyka.
    """
    parts: list[str] = []
    if animal.breed:
        parts.append(animal.breed)
    if animal.birth_date:
        parts.append(f"ur. {animal.birth_date.isoformat()}")
    parts.append(f"kolczyk {animal.ear_tag}")

    text = ", ".join(parts)
    return text[:1].upper() + text[1:] + "."
