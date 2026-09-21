from datetime import date, datetime

from pydantic import BaseModel, ConfigDict, Field

from app.models import EventType, IrzStatus, MeasurementType, Sex


class AnimalBase(BaseModel):
    ear_tag: str = Field(min_length=14, max_length=14, pattern=r"^PL\d{12}$")
    name: str | None = None
    birth_date: date | None = None
    breed: str | None = None
    sex: Sex = Sex.FEMALE
    mother_ear_tag: str | None = None
    herd_id: str | None = None


class AnimalCreate(AnimalBase):
    pass


class AnimalRead(AnimalBase):
    model_config = ConfigDict(from_attributes=True)
    id: int
    created_at: datetime
    updated_at: datetime | None = None


class IrzResult(BaseModel):
    """Stan zgłoszenia zdarzenia do IRZplus — ustala aplikacja mobilna po odpowiedzi IRZ."""

    irz_status: IrzStatus = IrzStatus.PENDING
    irz_submitted_at: datetime | None = None
    irz_message: str | None = None
    irz_document_number: str | None = None


class EventBase(BaseModel):
    animal_id: int
    type: EventType
    event_date: date
    notes: str | None = None
    partner_holding_number: str | None = None


class EventCreate(EventBase, IrzResult):
    pass


class EventRead(EventBase, IrzResult):
    model_config = ConfigDict(from_attributes=True)
    id: int
    created_at: datetime
    updated_at: datetime | None = None


class TreatmentBase(BaseModel):
    animal_id: int
    drug: str = Field(min_length=1, max_length=128)
    dose: str | None = None
    vet: str | None = None
    treatment_date: date
    withdrawal_until: date | None = None
    notes: str | None = None


class TreatmentCreate(TreatmentBase):
    pass


class TreatmentRead(TreatmentBase):
    model_config = ConfigDict(from_attributes=True)
    id: int
    updated_at: datetime | None = None


class MeasurementBase(BaseModel):
    animal_id: int
    type: MeasurementType
    value: float
    measurement_date: date
    photo_uri: str | None = None


class MeasurementRead(MeasurementBase):
    model_config = ConfigDict(from_attributes=True)
    id: int


# --- Synchronizacja przyrostowa (N5) ------------------------------------

class DeletionRead(BaseModel):
    """Informacja, że rekord o tym identyfikatorze przestał istnieć na serwerze."""

    model_config = ConfigDict(from_attributes=True)
    entity: str = Field(description="animal | event | treatment")
    entity_id: int
    deleted_at: datetime


class SyncChanges(BaseModel):
    """Odpowiedź ``GET /sync/changes`` — tylko to, co zmieniło się od ``since``.

    ``server_time`` aplikacja mobilna zapisuje u siebie i podaje jako ``since``
    w kolejnym zapytaniu. Bierzemy czas serwera, a nie telefonu, żeby rozjechany
    zegar urządzenia nie gubił rekordów.
    """

    server_time: datetime
    since: datetime | None = None
    animals: list[AnimalRead] = Field(default_factory=list)
    events: list[EventRead] = Field(default_factory=list)
    treatments: list[TreatmentRead] = Field(default_factory=list)
    deletions: list[DeletionRead] = Field(default_factory=list)
