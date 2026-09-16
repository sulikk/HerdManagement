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
