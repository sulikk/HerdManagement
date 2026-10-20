"""Synchronizacja przyrostowa między aplikacją mobilną a backendem (wymaganie N5).

Umożliwia aplikacji pobranie zmian wprowadzonych poza urządzeniem, np. przez asystenta
LLM. Endpoint zwraca rekordy zmienione po ``since`` (brak parametru oznacza pełną
migawkę) oraz znaczniki usunięcia rekordów.
"""
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app import crud, schemas
from app.database import get_db

router = APIRouter(prefix="/sync", tags=["sync"])


@router.get("/changes", response_model=schemas.SyncChanges)
def changes(
    since: datetime | None = Query(
        default=None,
        description="Znacznik z poprzedniej synchronizacji (server_time). "
        "Pusty = pełna migawka.",
    ),
    db: Session = Depends(get_db),
):
    # Czas serwera jest odczytywany przed zapytaniem, aby rekordy zapisane w trakcie
    # jego wykonywania trafiły do kolejnej porcji.
    server_time = datetime.now(timezone.utc)
    payload = crud.changes_since(db, _as_utc(since))
    return schemas.SyncChanges(server_time=server_time, since=since, **payload)


def _as_utc(moment: datetime | None) -> datetime | None:
    """Konwertuje znacznik czasu do UTC bez informacji o strefie, zgodnie z formatem bazy.

    SQLite przechowuje daty jako tekst bez przesunięcia strefowego, dlatego znacznik
    z przesunięciem (np. +02:00) musi zostać sprowadzony do UTC przed porównaniem.
    """
    if moment is None or moment.tzinfo is None:
        return moment
    return moment.astimezone(timezone.utc).replace(tzinfo=None)
