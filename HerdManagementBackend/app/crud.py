from sqlalchemy.orm import Session

from app import models, schemas


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


def delete_event(db: Session, event_id: int) -> bool:
    event = db.get(models.Event, event_id)
    if event is None:
        return False
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
    db.delete(treatment)
    db.commit()
    return True
