from collections.abc import Iterator

from sqlalchemy import Engine, create_engine, inspect, text
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.config import settings

connect_args = {"check_same_thread": False} if settings.database_url.startswith("sqlite") else {}
engine = create_engine(settings.database_url, connect_args=connect_args, future=True)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, future=True)


class Base(DeclarativeBase):
    pass


def get_db() -> Iterator[Session]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_db(bind: Engine = engine) -> None:
    from app import models  # noqa: F401  rejestracja modeli w Base.metadata

    Base.metadata.create_all(bind=bind)
    add_missing_columns(bind)
    backfill_sync_columns(bind)


def add_missing_columns(bind: Engine) -> list[str]:
    """Dokłada do istniejących tabel nowe kolumny dopuszczające NULL.

    `create_all` nie modyfikuje istniejących tabel, więc po rozbudowie modelu stara
    baza deweloperska (herd.db) nie miałaby nowych pól. Ta prosta migracja wystarcza
    na etap prototypu; zmiany typów czy usuwanie kolumn wymagałyby Alembica.
    """
    added: list[str] = []
    inspector = inspect(bind)
    with bind.begin() as conn:
        for table in Base.metadata.sorted_tables:
            existing = {c["name"] for c in inspector.get_columns(table.name)}
            for column in table.columns:
                if column.name in existing or not column.nullable:
                    continue
                ddl_type = column.type.compile(dialect=bind.dialect)
                conn.execute(text(f'ALTER TABLE "{table.name}" ADD COLUMN "{column.name}" {ddl_type}'))
                added.append(f"{table.name}.{column.name}")
    return added


def backfill_sync_columns(bind: Engine) -> int:
    """Uzupełnia ``updated_at`` w wierszach sprzed dodania tej kolumny.

    Synchronizacja przyrostowa filtruje po ``updated_at > since``, a porównanie
    z NULL nigdy nie jest prawdziwe — bez tego kroku dane zapisane przed migracją
    nigdy nie trafiłyby na telefon. Uzupełniamy datą utworzenia (a gdy i jej brak,
    bieżącą), więc takie wiersze przyjdą raz i potem się uspokoją.
    """
    inspector = inspect(bind)
    updated = 0
    with bind.begin() as conn:
        for table_name in ("animals", "events", "treatments"):
            if not inspector.has_table(table_name):
                continue
            columns = {c["name"] for c in inspector.get_columns(table_name)}
            if "updated_at" not in columns:
                continue
            fallback = "created_at" if "created_at" in columns else "CURRENT_TIMESTAMP"
            result = conn.execute(
                text(
                    f'UPDATE "{table_name}" '
                    f"SET updated_at = COALESCE({fallback}, CURRENT_TIMESTAMP) "
                    "WHERE updated_at IS NULL"
                )
            )
            updated += result.rowcount or 0
    return updated
