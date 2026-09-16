from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app import crud, schemas
from app.database import get_db

router = APIRouter(prefix="/animals", tags=["animals"])


@router.get("", response_model=list[schemas.AnimalRead])
def list_animals(skip: int = 0, limit: int = 100, db: Session = Depends(get_db)):
    return crud.list_animals(db, skip=skip, limit=limit)


@router.get("/{animal_id}", response_model=schemas.AnimalRead)
def get_animal(animal_id: int, db: Session = Depends(get_db)):
    animal = crud.get_animal(db, animal_id)
    if animal is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Animal not found")
    return animal


@router.post("", response_model=schemas.AnimalRead, status_code=status.HTTP_201_CREATED)
def create_animal(payload: schemas.AnimalCreate, db: Session = Depends(get_db)):
    if crud.get_animal_by_ear_tag(db, payload.ear_tag) is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Animal with this ear tag already exists",
        )
    return crud.create_animal(db, payload)


@router.put("/{animal_id}", response_model=schemas.AnimalRead)
def update_animal(animal_id: int, payload: schemas.AnimalCreate, db: Session = Depends(get_db)):
    animal = crud.get_animal(db, animal_id)
    if animal is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Animal not found")
    other = crud.get_animal_by_ear_tag(db, payload.ear_tag)
    if other is not None and other.id != animal_id:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Animal with this ear tag already exists",
        )
    return crud.update_animal(db, animal, payload)


@router.delete("/{animal_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_animal(animal_id: int, db: Session = Depends(get_db)):
    if not crud.delete_animal(db, animal_id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Animal not found")
