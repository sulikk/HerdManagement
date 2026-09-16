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


def test_migracja_jest_idempotentna():
    engine = create_engine("sqlite://", poolclass=StaticPool)
    init_db(engine)

    assert add_missing_columns(engine) == []
