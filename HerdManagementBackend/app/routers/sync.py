"""Synchronizacja przyrostowa telefon <-> backend (wymaganie N5).

Aplikacja mobilna trzyma własną kopię danych w Room i działa offline, więc musi
umieć dociągnąć zmiany, których sama nie wykonała: wpisy dodane przez asystenta
LLM, dane z innego urządzenia albo poprawki zrobione bezpośrednio w backendzie.
Bez tego karta zwierzęcia na telefonie i ewidencja na serwerze rozjeżdżają się
po cichu — a hodowca podejmuje decyzje na podstawie tego, co widzi w telefonie.

Endpoint zwraca wyłącznie rekordy zmienione po ``since`` (pusty ``since`` daje
pełną migawkę przy pierwszym uruchomieniu) oraz nagrobki po usuniętych wierszach.
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
    # Czas odczytujemy PRZED zapytaniem: gdyby ktoś zapisał rekord w trakcie,
    # trafi on do następnej porcji zamiast wpaść w lukę między zapytaniem a zegarem.
    server_time = datetime.now(timezone.utc)
    payload = crud.changes_since(db, _as_utc(since))
    return schemas.SyncChanges(server_time=server_time, since=since, **payload)


def _as_utc(moment: datetime | None) -> datetime | None:
    """Sprowadza znacznik do UTC bez strefy — w takiej postaci leżą daty w bazie.

    SQLite przechowuje daty jako tekst bez przesunięcia strefowego. Gdyby telefon
    przysłał ``since`` z offsetem (np. +02:00), porównanie poszłoby po samej
    godzinie ściennej i synchronizacja zgubiłaby dwie godziny zmian.
    """
    if moment is None or moment.tzinfo is None:
        return moment
    return moment.astimezone(timezone.utc).replace(tzinfo=None)
