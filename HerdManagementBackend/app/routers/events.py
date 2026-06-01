from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app import crud, schemas
from app.database import get_db

router = APIRouter(tags=["events"])


@router.get("/animals/{animal_id}/events", response_model=list[schemas.EventRead])
def list_events(animal_id: int, db: Session = Depends(get_db)):
    if crud.get_animal(db, animal_id) is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Animal not found")
    return crud.list_events(db, animal_id)


@router.post(
    "/animals/{animal_id}/events",
    response_model=schemas.EventRead,
    status_code=status.HTTP_201_CREATED,
)
def create_event(animal_id: int, payload: schemas.EventCreate, db: Session = Depends(get_db)):
    if crud.get_animal(db, animal_id) is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Animal not found")
    if payload.animal_id != animal_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="animal_id in body does not match path",
        )
    return crud.create_event(db, payload)


@router.delete("/events/{event_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_event(event_id: int, db: Session = Depends(get_db)):
    if not crud.delete_event(db, event_id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Event not found")
