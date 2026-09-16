"""Wspólne fixtury testów backendu.

Każdy test dostaje:
- świeżą bazę SQLite w pamięci (nie dotyka herd.db),
- świeży stan mocka IRZplus (tokeny, rejestr zwierząt, liczniki dokumentów).
"""
from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.database import get_db, init_db
from app.irzplus import router as irzplus_router
from app.irzplus.service import IrzplusStore
from main import app

PRODUCENT = "123456789"
SIEDZIBA = "PL000111222"
SEED_ZWIERZE = "PL005338887002"


@pytest.fixture
def irz_store(monkeypatch: pytest.MonkeyPatch) -> IrzplusStore:
    """Podmienia współdzielony stan mocka na nową instancję (izolacja testów)."""
    fresh = IrzplusStore()
    monkeypatch.setattr(irzplus_router, "store", fresh)
    return fresh


@pytest.fixture
def client(irz_store: IrzplusStore) -> Iterator[TestClient]:
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    init_db(engine)
    testing_session = sessionmaker(bind=engine, autoflush=False, autocommit=False)

    def override_get_db() -> Iterator[Session]:
        db = testing_session()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    # Bez bloku `with` — lifespan (init_db na pliku herd.db) się nie uruchamia.
    yield TestClient(app)
    app.dependency_overrides.clear()
    engine.dispose()


@pytest.fixture
def auth_headers(client: TestClient) -> dict[str, str]:
    response = client.post("/irzplus/logowanie", json={"login": PRODUCENT, "haslo": "demo"})
    assert response.status_code == 200
    return {"Authorization": f"Bearer {response.json()['token']}"}
