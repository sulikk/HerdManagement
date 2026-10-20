"""Stan i logika symulatora IRZplus.

Symulator przechowuje w pamięci rejestr zwierząt, wydane tokeny oraz przyjęte
dokumenty. Stan jest tracony przy restarcie procesu.
"""
import re
import secrets
import threading
from datetime import date, datetime, timedelta, timezone

from app.irzplus.schemas import (
    BladWalidacjiDTO,
    PozycjaZgloszeniaDTO,
    ZwierzeIndywidualneDTO,
)

EAR_TAG_PATTERN = re.compile(r"^PL\d{12}$")
TOKEN_TTL_SECONDS = 3600


class IrzplusStore:
    """Magazyn stanu symulatora w pamięci, bezpieczny przy współbieżnych żądaniach."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._tokens: dict[str, tuple[str, datetime]] = {}  # token -> (producent, termin ważności)
        self._animals: dict[str, ZwierzeIndywidualneDTO] = {}
        self._closed: set[str] = set()  # zwierzęta rozliczone (ubyłe/padłe)
        self._documents: list[dict] = []
        self._counters: dict[str, int] = {}
        self._seed()

    def _seed(self) -> None:
        seed = ZwierzeIndywidualneDTO(
            numerIdentyfikacyjnyZwierzecia="PL005338887002",
            numerSiedzibyStada="PL000111222",
            plec="samica",
            rasa="HO",
            dataUrodzenia=date(2023, 4, 1),
        )
        self._animals[seed.numerIdentyfikacyjnyZwierzecia] = seed

    def issue_token(self, numer_producenta: str) -> tuple[str, int]:
        token = secrets.token_urlsafe(24)
        expiry = datetime.now(timezone.utc) + timedelta(seconds=TOKEN_TTL_SECONDS)
        with self._lock:
            self._tokens[token] = (numer_producenta, expiry)
        return token, TOKEN_TTL_SECONDS

    def resolve_token(self, token: str) -> str | None:
        with self._lock:
            entry = self._tokens.get(token)
            if entry is None:
                return None
            producent, expiry = entry
            if datetime.now(timezone.utc) > expiry:
                del self._tokens[token]
                return None
            return producent

    def list_animals(self) -> list[ZwierzeIndywidualneDTO]:
        with self._lock:
            return list(self._animals.values())

    def get_animal(self, numer: str) -> ZwierzeIndywidualneDTO | None:
        with self._lock:
            return self._animals.get(numer)

    def register_animal(self, animal: ZwierzeIndywidualneDTO) -> None:
        with self._lock:
            self._animals[animal.numerIdentyfikacyjnyZwierzecia] = animal
            self._closed.discard(animal.numerIdentyfikacyjnyZwierzecia)

    def is_known(self, numer: str) -> bool:
        with self._lock:
            return numer in self._animals

    def is_closed(self, numer: str) -> bool:
        with self._lock:
            return numer in self._closed

    def close_animal(self, numer: str) -> None:
        with self._lock:
            self._closed.add(numer)

    def next_document_number(self, prefix: str) -> str:
        with self._lock:
            year = date.today().year
            key = f"{prefix}/{year}"
            self._counters[key] = self._counters.get(key, 0) + 1
            return f"{prefix}/{year}/{self._counters[key]:06d}"

    def add_document(self, numer_dokumentu: str, kod: str, pozycje: list[PozycjaZgloszeniaDTO]) -> None:
        with self._lock:
            self._documents.append(
                {
                    "numerDokumentu": numer_dokumentu,
                    "kod": kod,
                    "liczbaPozycji": len(pozycje),
                    "przyjeto": datetime.now(timezone.utc).isoformat(),
                },
            )


PLEC_DOZWOLONE = {"samica", "samiec", "nieokreslona"}
MAX_OPOZNIENIE_DNI = 7  # ustawowy termin zgłoszenia zdarzenia do IRZ


def cyfra_kontrolna(pierwsze_11_cyfr: str) -> int:
    """Oblicza cyfrę kontrolną numeru bydła: wagi 3, 1, 3, 1, …, wynik (10 - suma mod 10) mod 10.

    Algorytm ustalono empirycznie na numerach z katalogów buhajów; rozporządzenie
    określa wyłącznie pozycję cyfry kontrolnej.
    """
    suma = sum(int(c) * (3 if i % 2 == 0 else 1) for i, c in enumerate(pierwsze_11_cyfr))
    return (10 - suma % 10) % 10


def poprawny_numer_zwierzecia(numer: str) -> bool:
    """Sprawdza format ``PL`` + 12 cyfr oraz poprawność cyfry kontrolnej."""
    return bool(EAR_TAG_PATTERN.match(numer)) and cyfra_kontrolna(numer[2:13]) == int(numer[13])


def _blad(kod: str, opis: str, numer: str) -> BladWalidacjiDTO:
    return BladWalidacjiDTO(kod=kod, opis=opis, numerZwierzecia=numer)


def validate_common(pozycja: PozycjaZgloszeniaDTO) -> list[BladWalidacjiDTO]:
    """Walidacja wspólna dla wszystkich typów zgłoszeń bydła."""
    numer = pozycja.numerIdentyfikacyjnyZwierzecia
    bledy: list[BladWalidacjiDTO] = []
    if not EAR_TAG_PATTERN.match(numer):
        bledy.append(_blad("ERR_NUMER_ZWIERZECIA",
                           "Numer identyfikacyjny zwierzęcia musi mieć format PL + 12 cyfr.", numer))
    elif not poprawny_numer_zwierzecia(numer):
        bledy.append(_blad("ERR_CYFRA_KONTROLNA",
                           "Numer identyfikacyjny zwierzęcia ma nieprawidłową cyfrę kontrolną.", numer))
    if not pozycja.numerSiedzibyStada:
        bledy.append(_blad("ERR_SIEDZIBA_STADA", "Brak numeru siedziby stada.", numer))
    if pozycja.dataZdarzenia > date.today():
        bledy.append(_blad("ERR_DATA_PRZYSZLA", "Data zdarzenia nie może być z przyszłości.", numer))
    return bledy


def validate_urodzenie(store: "IrzplusStore", pozycja: PozycjaZgloszeniaDTO) -> list[BladWalidacjiDTO]:
    numer = pozycja.numerIdentyfikacyjnyZwierzecia
    bledy = validate_common(pozycja)
    if pozycja.dataUrodzenia is None:
        bledy.append(_blad("ERR_BRAK_DATY_URODZENIA", "Zgłoszenie urodzenia wymaga daty urodzenia.", numer))
    if not pozycja.plec or pozycja.plec not in PLEC_DOZWOLONE:
        bledy.append(_blad("ERR_PLEC", "Pole płeć jest wymagane (samica/samiec).", numer))
    if not pozycja.numerIdentyfikacyjnyMatki:
        bledy.append(_blad("ERR_BRAK_MATKI", "Zgłoszenie urodzenia bydła wymaga numeru matki.", numer))
    elif not poprawny_numer_zwierzecia(pozycja.numerIdentyfikacyjnyMatki):
        bledy.append(_blad("ERR_NUMER_MATKI",
                           "Numer matki musi mieć format PL + 12 cyfr z poprawną cyfrą kontrolną.", numer))
    if store.is_known(numer):
        bledy.append(_blad("ERR_ZWIERZE_ISTNIEJE", "Zwierzę o tym numerze jest już zarejestrowane w IRZ.", numer))
    if (pozycja.dataUrodzenia is not None
            and pozycja.dataZdarzenia < pozycja.dataUrodzenia):
        bledy.append(_blad("ERR_DATA_PRZED_URODZENIEM", "Data zdarzenia nie może być wcześniejsza niż data urodzenia.", numer))
    return bledy


def validate_przybycie(store: "IrzplusStore", pozycja: PozycjaZgloszeniaDTO) -> list[BladWalidacjiDTO]:
    numer = pozycja.numerIdentyfikacyjnyZwierzecia
    bledy = validate_common(pozycja)
    if not pozycja.numerSiedzibyStadaPartnera:
        bledy.append(_blad("ERR_BRAK_SIEDZIBY_PARTNERA", "Zgłoszenie przybycia wymaga numeru siedziby, z której zwierzę przybyło.", numer))
    return bledy


def validate_ubycie(store: "IrzplusStore", pozycja: PozycjaZgloszeniaDTO) -> list[BladWalidacjiDTO]:
    numer = pozycja.numerIdentyfikacyjnyZwierzecia
    bledy = validate_common(pozycja)
    if not pozycja.numerSiedzibyStadaPartnera:
        bledy.append(_blad("ERR_BRAK_SIEDZIBY_PARTNERA", "Zgłoszenie ubycia wymaga numeru siedziby, do której zwierzę ubyło.", numer))
    if not store.is_known(numer):
        bledy.append(_blad("ERR_ZWIERZE_NIEZNANE", "Zwierzę nie jest zarejestrowane w IRZ — nie można zgłosić ubycia.", numer))
    elif store.is_closed(numer):
        bledy.append(_blad("ERR_ZWIERZE_ROZLICZONE", "Zwierzę zostało już rozliczone (ubyło lub padło).", numer))
    return bledy


def validate_padniecie(store: "IrzplusStore", pozycja: PozycjaZgloszeniaDTO) -> list[BladWalidacjiDTO]:
    numer = pozycja.numerIdentyfikacyjnyZwierzecia
    bledy = validate_common(pozycja)
    if not store.is_known(numer):
        bledy.append(_blad("ERR_ZWIERZE_NIEZNANE", "Zwierzę nie jest zarejestrowane w IRZ — nie można zgłosić padnięcia.", numer))
    elif store.is_closed(numer):
        bledy.append(_blad("ERR_ZWIERZE_ROZLICZONE", "Zwierzę zostało już rozliczone (ubyło lub padło).", numer))
    return bledy


# Instancja współdzielona przez router; stan symulatora jest stanem procesu.
store = IrzplusStore()
