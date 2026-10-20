from datetime import date, datetime

from pydantic import BaseModel, ConfigDict, Field

from app.models import EventType, IrzStatus, ListingStatus, MeasurementType, Sex


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
    """Wynik zgłoszenia zdarzenia do IRZplus, ustalany przez aplikację mobilną."""

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


class ListingBase(BaseModel):
    animal_id: int
    price: float = Field(gt=0, description="Cena wywoławcza w złotych.")
    description: str | None = Field(default=None, max_length=2000)
    photo_uri: str | None = None


class ListingCreate(ListingBase):
    pass


class ListingUpdate(BaseModel):
    """Edycja treści ogłoszenia; status i odznakę zmieniają wyłącznie ``/publish`` i ``/verify``."""

    price: float = Field(gt=0)
    description: str | None = Field(default=None, max_length=2000)
    photo_uri: str | None = None


class ListingRead(ListingBase):
    model_config = ConfigDict(from_attributes=True)
    id: int
    status: ListingStatus
    published_at: datetime | None = None
    irz_verified: bool
    irz_verified_at: datetime | None = None
    irz_message: str | None = None
    created_at: datetime
    updated_at: datetime | None = None


class ListingDraft(BaseModel):
    """Dane karty zwierzęcia do wstępnego wypełnienia formularza ogłoszenia (wymaganie F13)."""

    animal_id: int
    ear_tag: str
    name: str | None = None
    birth_date: date | None = None
    breed: str | None = None
    sex: Sex
    herd_id: str | None = None
    suggested_description: str
    existing_listing_id: int | None = Field(
        default=None,
        description="Ustawione, gdy zwierzę ma już szkic albo opublikowane ogłoszenie.",
    )


class ListingVerification(BaseModel):
    """Wynik weryfikacji ogłoszenia w rejestrze IRZplus (wymaganie F14)."""

    listing_id: int
    irz_verified: bool
    irz_verified_at: datetime | None = None
    message: str | None = None
    discrepancies: list[str] = Field(default_factory=list)


class InquiryBase(BaseModel):
    buyer: str = Field(min_length=1, max_length=128)
    contact: str | None = Field(default=None, max_length=128)
    message: str = Field(min_length=1, max_length=2000)


class InquiryCreate(InquiryBase):
    pass


class InquiryRead(InquiryBase):
    model_config = ConfigDict(from_attributes=True)
    id: int
    listing_id: int
    created_at: datetime
    updated_at: datetime | None = None


class DeletionRead(BaseModel):
    """Znacznik usunięcia rekordu po stronie serwera."""

    model_config = ConfigDict(from_attributes=True)
    entity: str = Field(description="animal | event | treatment")
    entity_id: int
    deleted_at: datetime


class SyncChanges(BaseModel):
    """Odpowiedź ``GET /sync/changes`` zawierająca zmiany od ``since``.

    Wartość ``server_time`` jest przekazywana przez aplikację jako ``since`` w kolejnym
    zapytaniu, co uniezależnia synchronizację od zegara urządzenia.
    """

    server_time: datetime
    since: datetime | None = None
    animals: list[AnimalRead] = Field(default_factory=list)
    events: list[EventRead] = Field(default_factory=list)
    treatments: list[TreatmentRead] = Field(default_factory=list)
    listings: list[ListingRead] = Field(default_factory=list)
    inquiries: list[InquiryRead] = Field(default_factory=list)
    deletions: list[DeletionRead] = Field(default_factory=list)
