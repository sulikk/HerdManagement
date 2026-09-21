import logging
from collections.abc import Iterator
from enum import Enum

from sqlalchemy import Engine, create_engine, inspect, text
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.config import settings

logger = logging.getLogger(__name__)

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
    """Dokłada do istniejących tabel kolumny, które doszły do modelu.

    `create_all` nie modyfikuje istniejących tabel, więc po rozbudowie modelu stara
    baza deweloperska (herd.db) nie miałaby nowych pól. Ta prosta migracja wystarcza
    na etap prototypu; zmiany typów czy usuwanie kolumn wymagałyby Alembica.

    Kolumny ``NOT NULL`` wymagają wartości domyślnej — SQLite nie ma czym wypełnić
    istniejących wierszy. Gdy model podaje stały default (np. ``status`` ogłoszenia),
    dokładamy je jako ``NOT NULL DEFAULT ...``; bez defaultu kolumna jest pomijana,
    bo takiej migracji nie da się wykonać bezpiecznie w locie.
    """
    added: list[str] = []
    inspector = inspect(bind)
    with bind.begin() as conn:
        for table in Base.metadata.sorted_tables:
            # Tabeli może jeszcze nie być (create_all tworzy ją dopiero obok) —
            # wtedy nie ma czego uzupełniać.
            if not inspector.has_table(table.name):
                continue
            existing = {c["name"] for c in inspector.get_columns(table.name)}
            for column in table.columns:
                if column.name in existing:
                    continue

                ddl_type = column.type.compile(dialect=bind.dialect)
                constraint = ""
                if not column.nullable:
                    literal = _default_literal(column)
                    if literal is None:
                        logger.warning(
                            "Pomijam kolumnę %s.%s — NOT NULL bez wartości domyślnej "
                            "wymaga ręcznej migracji.",
                            table.name,
                            column.name,
                        )
                        continue
                    constraint = f" NOT NULL DEFAULT {literal}"

                conn.execute(
                    text(
                        f'ALTER TABLE "{table.name}" '
                        f'ADD COLUMN "{column.name}" {ddl_type}{constraint}'
                    )
                )
                added.append(f"{table.name}.{column.name}")
    return added


def _default_literal(column) -> str | None:
    """Stała domyślna kolumny w postaci literału SQL albo ``None``, gdy jej nie ma.

    Obsługuje tylko wartości stałe. Default wyliczany funkcją (jak ``_utcnow``)
    zależy od momentu zapisu i nie ma sensu jako wypełniacz dla starych wierszy.
    """
    default = column.default
    if default is None or not getattr(default, "is_scalar", False):
        return None

    value = default.arg
    if isinstance(value, Enum):
        # SQLAlchemy zapisuje enumy pod nazwą składowej (DRAFT), nie wartością.
        value = value.name
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, str):
        escaped = value.replace("'", "''")
        return f"'{escaped}'"
    return None


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
