"""Schematy symulatora API IRZplus, wzorowane na specyfikacji OpenAPI ARiMR.

Konwencje zgodne z dokumentacją gov.pl/web/arimr/api--portal-irzplus:
- nazwy pól w języku polskim w notacji camelCase,
- zgłoszenie w postaci dyspozycji z listą pozycji,
- odpowiedź z numerem dokumentu i listą błędów walidacji.
"""
from datetime import date

from pydantic import BaseModel, ConfigDict, Field


class LoginRequest(BaseModel):
    login: str = Field(description="Numer producenta (EP) lub PESEL")
    haslo: str


class LoginResponse(BaseModel):
    token: str
    typTokenu: str = "Bearer"
    wygasaZaSekundy: int


class PozycjaZgloszeniaDTO(BaseModel):
    """Pozycja zgłoszenia; zestaw wymaganych pól zależy od typu dokumentu."""

    model_config = ConfigDict(populate_by_name=True)

    numerIdentyfikacyjnyZwierzecia: str
    numerSiedzibyStada: str
    dataZdarzenia: date
    # Pola zgłoszenia urodzenia
    numerIdentyfikacyjnyMatki: str | None = None
    plec: str | None = None
    rasa: str | None = None
    dataUrodzenia: date | None = None
    # Pola zgłoszenia przybycia i ubycia
    numerSiedzibyStadaPartnera: str | None = None
    # Pola zgłoszenia padnięcia
    przyczynaPadniecia: str | None = None


class ZgloszenieDTO(BaseModel):
    pozycje: list[PozycjaZgloszeniaDTO]


class DyspozycjaZgloszenia(BaseModel):
    numerProducenta: str
    komorkaOrganizacyjna: str | None = None
    zgloszenie: ZgloszenieDTO


class BladWalidacjiDTO(BaseModel):
    kod: str
    opis: str
    numerZwierzecia: str | None = None


class ZlozenieDyspozycjiResponse(BaseModel):
    numerDokumentu: str | None = None
    komunikat: str
    bledy: list[BladWalidacjiDTO] = Field(default_factory=list)


class ZwierzeIndywidualneDTO(BaseModel):
    numerIdentyfikacyjnyZwierzecia: str
    numerSiedzibyStada: str | None = None
    plec: str | None = None
    rasa: str | None = None
    dataUrodzenia: date | None = None
    numerIdentyfikacyjnyMatki: str | None = None
