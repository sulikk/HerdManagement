"""Test prostej migracji dokładającej nowe kolumny do starej bazy herd.db."""
from sqlalchemy import create_engine, inspect, text
from sqlalchemy.pool import StaticPool

from app.database import add_missing_columns, init_db


def test_stara_tabela_events_dostaje_nowe_kolumny():
    engine = create_engine("sqlite://", poolclass=StaticPool)
    with engine.begin() as conn:
        # Schemat events sprzed dodania siedziby partnera i wyniku IRZ.
        conn.execute(text(
            "CREATE TABLE events (id INTEGER PRIMARY KEY, animal_id INTEGER, type VARCHAR(9), "
            "event_date DATE, notes TEXT, irz_status VARCHAR(9), irz_submitted_at DATETIME, "
            "created_at DATETIME)",
        ))
        conn.execute(text(
            "INSERT INTO events (id, animal_id, type, event_date, irz_status) "
            "VALUES (1, 1, 'BIRTH', '2024-01-01', 'PENDING')",
        ))

    init_db(engine)

    kolumny = {c["name"] for c in inspect(engine).get_columns("events")}
    assert {"partner_holding_number", "irz_message", "irz_document_number"} <= kolumny
    with engine.connect() as conn:
        assert conn.execute(text("SELECT COUNT(*) FROM events")).scalar() == 1


def test_stara_tabela_listings_dostaje_kolumne_status():
    """Regresja: `status` jest NOT NULL, więc migracja musi podać wartość domyślną.

    Bez tego stara baza nie dostawała kolumny i każde zapytanie o ogłoszenia
    kończyło się `no such column: listings.status`.
    """
    engine = create_engine("sqlite://", poolclass=StaticPool)
    with engine.begin() as conn:
        # Schemat listings sprzed zadania 9 (bez statusu i pól weryfikacji).
        conn.execute(text(
            "CREATE TABLE listings (id INTEGER PRIMARY KEY, animal_id INTEGER, price FLOAT, "
            "description TEXT, irz_verified BOOLEAN, created_at DATETIME)",
        ))
        conn.execute(text(
            "INSERT INTO listings (id, animal_id, price, irz_verified) VALUES (1, 1, 5000.0, 0)",
        ))

    init_db(engine)

    kolumny = {c["name"] for c in inspect(engine).get_columns("listings")}
    assert {"status", "photo_uri", "published_at", "irz_verified_at", "irz_message"} <= kolumny
    with engine.connect() as conn:
        # Istniejący wiersz przeżywa migrację i dostaje sensowny status.
        assert conn.execute(text("SELECT status FROM listings WHERE id = 1")).scalar() == "DRAFT"


def test_kolumna_not_null_bez_domyslnej_jest_pomijana():
    """Takiej migracji nie da się wykonać w locie — ma być pominięta, nie wywalić startu."""
    from sqlalchemy import Column, Integer, String, Table

    from app.database import Base

    engine = create_engine("sqlite://", poolclass=StaticPool)
    Table(
        "wymagane_bez_domyslnej",
        Base.metadata,
        Column("id", Integer, primary_key=True),
        Column("obowiazkowe", String(16), nullable=False),
    )
    try:
        with engine.begin() as conn:
            conn.execute(text("CREATE TABLE wymagane_bez_domyslnej (id INTEGER PRIMARY KEY)"))

        added = add_missing_columns(engine)

        assert "wymagane_bez_domyslnej.obowiazkowe" not in added
    finally:
        Base.metadata.remove(Base.metadata.tables["wymagane_bez_domyslnej"])


def test_migracja_jest_idempotentna():
    engine = create_engine("sqlite://", poolclass=StaticPool)
    init_db(engine)

    assert add_missing_columns(engine) == []
