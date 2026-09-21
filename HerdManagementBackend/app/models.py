from datetime import date, datetime, timezone
from enum import Enum

from sqlalchemy import Boolean, Date, DateTime
from sqlalchemy import Enum as SAEnum
from sqlalchemy import Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class Sex(str, Enum):
    FEMALE = "female"
    MALE = "male"
    OTHER = "other"


class EventType(str, Enum):
    BIRTH = "birth"
    ARRIVAL = "arrival"
    DEPARTURE = "departure"
    DEATH = "death"


class IrzStatus(str, Enum):
    PENDING = "pending"
    SUBMITTED = "submitted"
    CONFIRMED = "confirmed"
    REJECTED = "rejected"


class MeasurementType(str, Enum):
    WEIGHT = "weight"
    BCS = "bcs"


class SyncedMixin:
    """Znacznik ostatniej modyfikacji — podstawa synchronizacji przyrostowej (N5).

    Aplikacja mobilna pyta ``GET /sync/changes?since=...`` i dostaje wyłącznie
    rekordy zmienione po tej dacie. Kolumna jest ``nullable``, żeby dało się ją
    dołożyć do istniejącej bazy przez ``add_missing_columns``; dla starych wierszy
    zapytania synchronizacji podstawiają ``created_at``.
    """

    updated_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), default=_utcnow, onupdate=_utcnow, nullable=True
    )


class Animal(SyncedMixin, Base):
    __tablename__ = "animals"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    ear_tag: Mapped[str] = mapped_column(String(14), unique=True, index=True)
    name: Mapped[str | None] = mapped_column(String(64), nullable=True)
    birth_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    breed: Mapped[str | None] = mapped_column(String(64), nullable=True)
    sex: Mapped[Sex] = mapped_column(SAEnum(Sex), default=Sex.FEMALE)
    mother_ear_tag: Mapped[str | None] = mapped_column(String(14), nullable=True)
    herd_id: Mapped[str | None] = mapped_column(String(32), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)

    events: Mapped[list["Event"]] = relationship(back_populates="animal", cascade="all, delete-orphan")
    treatments: Mapped[list["Treatment"]] = relationship(back_populates="animal", cascade="all, delete-orphan")
    measurements: Mapped[list["Measurement"]] = relationship(back_populates="animal", cascade="all, delete-orphan")
    listings: Mapped[list["Listing"]] = relationship(back_populates="animal", cascade="all, delete-orphan")


class Event(SyncedMixin, Base):
    __tablename__ = "events"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    animal_id: Mapped[int] = mapped_column(ForeignKey("animals.id", ondelete="CASCADE"), index=True)
    type: Mapped[EventType] = mapped_column(SAEnum(EventType))
    event_date: Mapped[date] = mapped_column(Date)
    notes: Mapped[str | None] = mapped_column(Text, nullable=True)
    # Siedziba partnera: skąd przybyło (przybycie) / dokąd ubyło (ubycie).
    partner_holding_number: Mapped[str | None] = mapped_column(String(16), nullable=True)
    irz_status: Mapped[IrzStatus] = mapped_column(SAEnum(IrzStatus), default=IrzStatus.PENDING)
    irz_submitted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    irz_message: Mapped[str | None] = mapped_column(Text, nullable=True)
    irz_document_number: Mapped[str | None] = mapped_column(String(64), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)

    animal: Mapped[Animal] = relationship(back_populates="events")


class Treatment(SyncedMixin, Base):
    __tablename__ = "treatments"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    animal_id: Mapped[int] = mapped_column(ForeignKey("animals.id", ondelete="CASCADE"), index=True)
    drug: Mapped[str] = mapped_column(String(128))
    dose: Mapped[str | None] = mapped_column(String(64), nullable=True)
    vet: Mapped[str | None] = mapped_column(String(128), nullable=True)
    treatment_date: Mapped[date] = mapped_column(Date)
    withdrawal_until: Mapped[date | None] = mapped_column(Date, nullable=True)
    notes: Mapped[str | None] = mapped_column(Text, nullable=True)
    # Nullable, żeby `add_missing_columns` dołożyło kolumnę do istniejącej bazy.
    created_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), default=_utcnow, nullable=True
    )

    animal: Mapped[Animal] = relationship(back_populates="treatments")


class Measurement(Base):
    __tablename__ = "measurements"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    animal_id: Mapped[int] = mapped_column(ForeignKey("animals.id", ondelete="CASCADE"), index=True)
    type: Mapped[MeasurementType] = mapped_column(SAEnum(MeasurementType))
    value: Mapped[float] = mapped_column(Float)
    measurement_date: Mapped[date] = mapped_column(Date)
    photo_uri: Mapped[str | None] = mapped_column(String(512), nullable=True)

    animal: Mapped[Animal] = relationship(back_populates="measurements")


class Deletion(Base):
    """Nagrobek po usuniętym rekordzie.

    Usunięty wiersz znika z tabeli, więc zapytanie „co się zmieniło od daty X”
    nie miałoby jak go zgłosić — telefon trzymałby go w Room w nieskończoność.
    Każde usunięcie zostawia tu ślad, który synchronizacja przekazuje dalej.
    Przy kasowaniu zwierzęcia zapisujemy też nagrobki jego zdarzeń i leczeń,
    żeby nie polegać na kaskadzie po stronie telefonu.
    """

    __tablename__ = "deletions"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    entity: Mapped[str] = mapped_column(String(16), index=True)  # animal | event | treatment
    entity_id: Mapped[int] = mapped_column(Integer, index=True)
    deleted_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow, index=True)


class Listing(Base):
    __tablename__ = "listings"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    animal_id: Mapped[int] = mapped_column(ForeignKey("animals.id", ondelete="CASCADE"), index=True)
    price: Mapped[float] = mapped_column(Float)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    irz_verified: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)

    animal: Mapped[Animal] = relationship(back_populates="listings")
