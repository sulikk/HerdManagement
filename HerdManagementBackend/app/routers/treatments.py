from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app import crud, schemas
from app.database import get_db

router = APIRouter(tags=["treatments"])


@router.get("/animals/{animal_id}/treatments", response_model=list[schemas.TreatmentRead])
def list_treatments(animal_id: int, db: Session = Depends(get_db)):
    if crud.get_animal(db, animal_id) is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Animal not found")
    return crud.list_treatments(db, animal_id)


@router.post(
    "/animals/{animal_id}/treatments",
    response_model=schemas.TreatmentRead,
    status_code=status.HTTP_201_CREATED,
)
def create_treatment(animal_id: int, payload: schemas.TreatmentCreate, db: Session = Depends(get_db)):
    if crud.get_animal(db, animal_id) is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Animal not found")
    if payload.animal_id != animal_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="animal_id in body does not match path",
        )
    return crud.create_treatment(db, payload)


@router.delete("/treatments/{treatment_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_treatment(treatment_id: int, db: Session = Depends(get_db)):
    if not crud.delete_treatment(db, treatment_id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Treatment not found")
