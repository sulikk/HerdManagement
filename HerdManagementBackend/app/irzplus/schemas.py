"""Schematy mocka API IRZplus — wzorowane na realnych plikach OpenAPI ARiMR.

Konwencje odwzorowane z dokumentacji gov.pl/web/arimr/api--portal-irzplus:
- pola po polsku, camelCase (alias),
- zgłoszenie jako "dyspozycja" z listą pozycji,
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
    """Pojedyncza pozycja zgłoszenia. Część pól zależy od typu dokumentu."""

    model_config = ConfigDict(populate_by_name=True)

    numerIdentyfikacyjnyZwierzecia: str
    numerSiedzibyStada: str
    dataZdarzenia: date
    # urodzenie
    numerIdentyfikacyjnyMatki: str | None = None
    plec: str | None = None
    rasa: str | None = None
    dataUrodzenia: date | None = None
    # przemieszczenie (przybycie/ubycie)
    numerSiedzibyStadaPartnera: str | None = None
    # padnięcie
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
