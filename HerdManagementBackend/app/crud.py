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


def delete_animal(db: Session, animal_id: int) -> bool:
    animal = db.get(models.Animal, animal_id)
    if animal is None:
        return False
    db.delete(animal)
    db.commit()
    return True
