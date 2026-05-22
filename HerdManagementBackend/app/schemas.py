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


class EventBase(BaseModel):
    animal_id: int
    type: EventType
    event_date: date
    notes: str | None = None


class EventCreate(EventBase):
    pass


class EventRead(EventBase):
    model_config = ConfigDict(from_attributes=True)
    id: int
    irz_status: IrzStatus
    irz_submitted_at: datetime | None = None
    created_at: datetime


class MeasurementBase(BaseModel):
    animal_id: int
    type: MeasurementType
    value: float
    measurement_date: date
    photo_uri: str | None = None


class MeasurementRead(MeasurementBase):
    model_config = ConfigDict(from_attributes=True)
    id: int
