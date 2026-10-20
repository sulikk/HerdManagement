from datetime import datetime

from sqlalchemy import func
from sqlalchemy.orm import Session

from app import models, schemas

# Nazwy encji w znacznikach usunięcia, zgodne z aplikacją mobilną.
ENTITY_ANIMAL = "animal"
ENTITY_EVENT = "event"
ENTITY_TREATMENT = "treatment"
ENTITY_LISTING = "listing"
ENTITY_INQUIRY = "inquiry"


def _tombstone(db: Session, entity: str, entity_id: int) -> None:
    """Zapisuje znacznik usunięcia rekordu na potrzeby synchronizacji przyrostowej."""
    db.add(models.Deletion(entity=entity, entity_id=entity_id))


def list_animals(db: Session, skip: int = 0, limit: int = 100) -> list[models.Animal]:
    return db.query(models.Animal).offset(skip).limit(limit).all()


def get_animal(db: Session, animal_id: int) -> models.Animal | None:
    return db.get(models.Animal, animal_id)


def get_animal_by_ear_tag(db: Session, ear_tag: str) -> models.Animal | None:
    return db.query(models.Animal).filter(models.Animal.ear_tag == ear_tag).first()


def create_animal(db: Session, data: schemas.AnimalCreate) -> models.Animal:
    animal = models.Animal(**data.model_dump())
    db.add(animal)
    db.commit()
    db.refresh(animal)
    return animal


def update_animal(db: Session, animal: models.Animal, data: schemas.AnimalCreate) -> models.Animal:
    for field, value in data.model_dump().items():
        setattr(animal, field, value)
    db.commit()
    db.refresh(animal)
    return animal


def delete_animal(db: Session, animal_id: int) -> bool:
    animal = db.get(models.Animal, animal_id)
    if animal is None:
        return False
    # Znaczniki usunięcia rekordów zależnych są zapisywane jawnie, ponieważ usuwanie
    # kaskadowe ich nie tworzy.
    for event in animal.events:
        _tombstone(db, ENTITY_EVENT, event.id)
    for treatment in animal.treatments:
        _tombstone(db, ENTITY_TREATMENT, treatment.id)
    for listing in animal.listings:
        for inquiry in listing.inquiries:
            _tombstone(db, ENTITY_INQUIRY, inquiry.id)
        _tombstone(db, ENTITY_LISTING, listing.id)
    _tombstone(db, ENTITY_ANIMAL, animal_id)
    db.delete(animal)
    db.commit()
    return True


def list_events(db: Session, animal_id: int) -> list[models.Event]:
    return (
        db.query(models.Event)
        .filter(models.Event.animal_id == animal_id)
        .order_by(models.Event.event_date.desc())
        .all()
    )


def create_event(db: Session, data: schemas.EventCreate) -> models.Event:
    event = models.Event(**data.model_dump())
    db.add(event)
    db.commit()
    db.refresh(event)
    return event


def get_event(db: Session, event_id: int) -> models.Event | None:
    return db.get(models.Event, event_id)


def update_event_irz(db: Session, event: models.Event, data: schemas.IrzResult) -> models.Event:
    for field, value in data.model_dump().items():
        setattr(event, field, value)
    db.commit()
    db.refresh(event)
    return event


def delete_event(db: Session, event_id: int) -> bool:
    event = db.get(models.Event, event_id)
    if event is None:
        return False
    _tombstone(db, ENTITY_EVENT, event_id)
    db.delete(event)
    db.commit()
    return True


def list_treatments(db: Session, animal_id: int) -> list[models.Treatment]:
    return (
        db.query(models.Treatment)
        .filter(models.Treatment.animal_id == animal_id)
        .order_by(models.Treatment.treatment_date.desc())
        .all()
    )


def create_treatment(db: Session, data: schemas.TreatmentCreate) -> models.Treatment:
    treatment = models.Treatment(**data.model_dump())
    db.add(treatment)
    db.commit()
    db.refresh(treatment)
    return treatment


def delete_treatment(db: Session, treatment_id: int) -> bool:
    treatment = db.get(models.Treatment, treatment_id)
    if treatment is None:
        return False
    _tombstone(db, ENTITY_TREATMENT, treatment_id)
    db.delete(treatment)
    db.commit()
    return True


def list_listings(
    db: Session,
    status: models.ListingStatus | None = None,
    animal_id: int | None = None,
) -> list[models.Listing]:
    query = db.query(models.Listing)
    if status is not None:
        query = query.filter(models.Listing.status == status)
    if animal_id is not None:
        query = query.filter(models.Listing.animal_id == animal_id)
    return query.order_by(models.Listing.created_at.desc()).all()


def get_listing(db: Session, listing_id: int) -> models.Listing | None:
    return db.get(models.Listing, listing_id)


def get_active_listing_for_animal(db: Session, animal_id: int) -> models.Listing | None:
    """Zwraca aktywne ogłoszenie zwierzęcia (szkic lub opublikowane).

    Zwierzę może mieć tylko jedno aktywne ogłoszenie; ogłoszenia sprzedane
    i wycofane nie blokują utworzenia nowego.
    """
    return (
        db.query(models.Listing)
        .filter(
            models.Listing.animal_id == animal_id,
            models.Listing.status.in_([models.ListingStatus.DRAFT, models.ListingStatus.PUBLISHED]),
        )
        .first()
    )


def create_listing(db: Session, data: schemas.ListingCreate) -> models.Listing:
    listing = models.Listing(**data.model_dump())
    db.add(listing)
    db.commit()
    db.refresh(listing)
    return listing


def update_listing(
    db: Session, listing: models.Listing, data: schemas.ListingUpdate
) -> models.Listing:
    for field_name, value in data.model_dump().items():
        setattr(listing, field_name, value)
    # Zmiana treści unieważnia odznakę weryfikacji.
    listing.irz_verified = False
    listing.irz_verified_at = None
    listing.irz_message = None
    db.commit()
    db.refresh(listing)
    return listing


def set_listing_status(
    db: Session, listing: models.Listing, status: models.ListingStatus
) -> models.Listing:
    listing.status = status
    if status == models.ListingStatus.PUBLISHED and listing.published_at is None:
        listing.published_at = models.utcnow()
    db.commit()
    db.refresh(listing)
    return listing


def save_listing_verification(
    db: Session, listing: models.Listing, verified: bool, message: str | None
) -> models.Listing:
    listing.irz_verified = verified
    listing.irz_verified_at = models.utcnow()
    listing.irz_message = message
    db.commit()
    db.refresh(listing)
    return listing


def delete_listing(db: Session, listing_id: int) -> bool:
    listing = db.get(models.Listing, listing_id)
    if listing is None:
        return False
    for inquiry in listing.inquiries:
        _tombstone(db, ENTITY_INQUIRY, inquiry.id)
    _tombstone(db, ENTITY_LISTING, listing_id)
    db.delete(listing)
    db.commit()
    return True


def list_inquiries(db: Session, listing_id: int) -> list[models.Inquiry]:
    return (
        db.query(models.Inquiry)
        .filter(models.Inquiry.listing_id == listing_id)
        .order_by(models.Inquiry.created_at.desc())
        .all()
    )


def create_inquiry(
    db: Session, listing_id: int, data: schemas.InquiryCreate
) -> models.Inquiry:
    inquiry = models.Inquiry(listing_id=listing_id, **data.model_dump())
    db.add(inquiry)
    db.commit()
    db.refresh(inquiry)
    return inquiry


def delete_inquiry(db: Session, inquiry_id: int) -> bool:
    inquiry = db.get(models.Inquiry, inquiry_id)
    if inquiry is None:
        return False
    _tombstone(db, ENTITY_INQUIRY, inquiry_id)
    db.delete(inquiry)
    db.commit()
    return True


def _changed_since(db: Session, model: type, since: datetime | None) -> list:
    """Zwraca rekordy zmienione po ``since``; ``None`` oznacza pełną migawkę.

    Dla wierszy bez wartości ``updated_at`` jako datę modyfikacji przyjmuje się
    datę utworzenia (``COALESCE``).
    """
    query = db.query(model)
    if since is not None:
        query = query.filter(func.coalesce(model.updated_at, model.created_at) > since)
    return query.order_by(model.id).all()


# Model odpowiadający każdej encji ze znaczników usunięcia (zob. ``_live_deletions``).
_ENTITY_MODELS: dict[str, type] = {
    ENTITY_ANIMAL: models.Animal,
    ENTITY_EVENT: models.Event,
    ENTITY_TREATMENT: models.Treatment,
    ENTITY_LISTING: models.Listing,
    ENTITY_INQUIRY: models.Inquiry,
}


def _live_deletions(db: Session, deletions: list) -> list:
    """Pomija znaczniki usunięcia dotyczące identyfikatorów nadanych ponownie.

    SQLite nadaje klucz główny jako ``max(id) + 1``, więc identyfikator usuniętego
    wiersza może zostać ponownie wykorzystany. Aplikacja mobilna dopasowuje znaczniki
    po parze ``(encja, id)``, dlatego znacznik, którego identyfikator należy do
    istniejącego wiersza, jest pomijany.
    """
    live_ids: dict[str, set[int]] = {}
    result = []
    for tombstone in deletions:
        model = _ENTITY_MODELS.get(tombstone.entity)
        if model is None:
            result.append(tombstone)
            continue
        if tombstone.entity not in live_ids:
            live_ids[tombstone.entity] = {row[0] for row in db.query(model.id).all()}
        if tombstone.entity_id not in live_ids[tombstone.entity]:
            result.append(tombstone)
    return result


def changes_since(db: Session, since: datetime | None) -> dict[str, list]:
    """Zwraca wszystkie zmiany po ``since`` na potrzeby ``GET /sync/changes``."""
    deletions = db.query(models.Deletion)
    if since is not None:
        deletions = deletions.filter(models.Deletion.deleted_at > since)

    return {
        "animals": _changed_since(db, models.Animal, since),
        "events": _changed_since(db, models.Event, since),
        "treatments": _changed_since(db, models.Treatment, since),
        "listings": _changed_since(db, models.Listing, since),
        "inquiries": _changed_since(db, models.Inquiry, since),
        "deletions": _live_deletions(db, deletions.order_by(models.Deletion.id).all()),
    }
