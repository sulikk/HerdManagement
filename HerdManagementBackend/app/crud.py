from datetime import datetime

from sqlalchemy import func
from sqlalchemy.orm import Session

from app import models, schemas

# Nazwy encji w nagrobkach — ten sam słownik rozumie aplikacja mobilna.
ENTITY_ANIMAL = "animal"
ENTITY_EVENT = "event"
ENTITY_TREATMENT = "treatment"


def _tombstone(db: Session, entity: str, entity_id: int) -> None:
    """Zostawia ślad po usuniętym rekordzie dla synchronizacji przyrostowej."""
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
    # Nagrobki dzieci zapisujemy jawnie — kaskada usuwa je po cichu, a telefon
    # musi dostać komplet identyfikatorów do skasowania u siebie.
    for event in animal.events:
        _tombstone(db, ENTITY_EVENT, event.id)
    for treatment in animal.treatments:
        _tombstone(db, ENTITY_TREATMENT, treatment.id)
    _tombstone(db, ENTITY_ANIMAL, animal_id)
    db.delete(animal)
    db.commit()
    return True


# --- Events -------------------------------------------------------------

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


# --- Treatments ---------------------------------------------------------

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


# --- Synchronizacja przyrostowa (N5) ------------------------------------

def _changed_since(db: Session, model: type, since: datetime | None) -> list:
    """Rekordy zmienione po ``since``; ``None`` oznacza pełną migawkę.

    ``COALESCE`` na wypadek wierszy sprzed dodania kolumny ``updated_at`` —
    dla nich datą modyfikacji jest data utworzenia.
    """
    query = db.query(model)
    if since is not None:
        query = query.filter(func.coalesce(model.updated_at, model.created_at) > since)
    return query.order_by(model.id).all()


def changes_since(db: Session, since: datetime | None) -> dict[str, list]:
    """Wszystko, co zmieniło się po ``since`` — wsad dla ``GET /sync/changes``."""
    deletions = db.query(models.Deletion)
    if since is not None:
        deletions = deletions.filter(models.Deletion.deleted_at > since)

    return {
        "animals": _changed_since(db, models.Animal, since),
        "events": _changed_since(db, models.Event, since),
        "treatments": _changed_since(db, models.Treatment, since),
        "deletions": deletions.order_by(models.Deletion.id).all(),
    }
