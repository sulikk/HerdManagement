"""Narzędzia asystenta LLM stanowiące warstwę pośrednią między modelem a bazą danych.

Narzędzia dzielą się na dwie grupy:

* odczytowe (``READ_TOOLS``) — wykonywane natychmiast, nie modyfikują danych,
* zapisujące (``WRITE_TOOLS``) — wykonywane wyłącznie po potwierdzeniu przez użytkownika.

Nazwy narzędzi i parametrów są w języku polskim, zgodnie z językiem rozmowy,
co poprawia dobór argumentów przez model.
"""
import re
from datetime import date, datetime, timedelta
from typing import Any

from sqlalchemy.orm import Session

from app import crud, models, schemas

EAR_TAG_PATTERN = re.compile(r"^PL\d{12}$")
HOLDING_PATTERN = re.compile(r"^PL\d{9}$")
REPORTING_DAYS = 7  # ustawowy termin zgłoszenia zdarzenia do IRZplus

SEX_PL: dict[str, models.Sex] = {
    "samica": models.Sex.FEMALE,
    "samiec": models.Sex.MALE,
    "inna": models.Sex.OTHER,
}
SEX_LABEL = {v: k for k, v in SEX_PL.items()}

EVENT_PL: dict[str, models.EventType] = {
    "urodzenie": models.EventType.BIRTH,
    "przybycie": models.EventType.ARRIVAL,
    "ubycie": models.EventType.DEPARTURE,
    "padniecie": models.EventType.DEATH,
}
EVENT_LABEL = {v: k for k, v in EVENT_PL.items()}

IRZ_LABEL = {
    models.IrzStatus.PENDING: "niezgłoszone",
    models.IrzStatus.SUBMITTED: "wysłane, brak odpowiedzi",
    models.IrzStatus.CONFIRMED: "przyjęte przez IRZplus",
    models.IrzStatus.REJECTED: "odrzucone przez IRZplus",
}


class ToolError(Exception):
    """Błąd wykonania narzędzia zwracany modelowi jako ``tool_result`` z ``is_error``."""


_EAR_TAG_FIELD = {
    "type": "string",
    "description": "Numer kolczyka w formacie PL + 12 cyfr, np. PL005338887002.",
}
_DATE_FIELD = {"type": "string", "description": "Data w formacie RRRR-MM-DD."}

READ_TOOL_DEFINITIONS: list[dict[str, Any]] = [
    {
        "name": "lista_zwierzat",
        "description": (
            "Zwraca listę zwierząt w ewidencji gospodarstwa. Używaj, gdy użytkownik pyta "
            "o stado, szuka sztuki po fragmencie numeru, imieniu lub rasie."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "szukaj": {
                    "type": "string",
                    "description": "Opcjonalny fragment numeru kolczyka, imienia lub rasy.",
                },
                "limit": {
                    "type": "integer",
                    "description": "Maksymalna liczba zwracanych sztuk (domyślnie 50).",
                },
            },
            "required": [],
        },
    },
    {
        "name": "dane_zwierzecia",
        "description": (
            "Zwraca pełną kartę jednej sztuki: dane podstawowe, zdarzenia ze statusem "
            "zgłoszenia do IRZplus oraz leczenia z okresami karencji."
        ),
        "input_schema": {
            "type": "object",
            "properties": {"numer_kolczyka": _EAR_TAG_FIELD},
            "required": ["numer_kolczyka"],
        },
    },
    {
        "name": "statystyki_stada",
        "description": (
            "Podsumowanie stada: liczba sztuk w stadzie, podział wg płci i grup wiekowych, "
            "rasy, liczba zdarzeń oczekujących na zgłoszenie i aktywnych karencji."
        ),
        "input_schema": {"type": "object", "properties": {}, "required": []},
    },
    {
        "name": "zdarzenia_do_zgloszenia",
        "description": (
            "Lista zdarzeń, które nie zostały jeszcze potwierdzone przez IRZplus, "
            f"wraz z terminem {REPORTING_DAYS}-dniowym i liczbą pozostałych dni. "
            "Używaj przy pytaniach o terminy, zaległości i zgodność z przepisami."
        ),
        "input_schema": {"type": "object", "properties": {}, "required": []},
    },
    {
        "name": "aktywne_karencje",
        "description": (
            "Lista leczeń z trwającym okresem karencji (mleko/mięso nie mogą trafić do obrotu). "
            "Używaj przy pytaniach typu „którego zwierzęcia nie mogę sprzedać albo doić”."
        ),
        "input_schema": {"type": "object", "properties": {}, "required": []},
    },
]

WRITE_TOOL_DEFINITIONS: list[dict[str, Any]] = [
    {
        "name": "dodaj_zwierze",
        "description": (
            "Dodaje nową sztukę do ewidencji gospodarstwa. Wymaga potwierdzenia użytkownika. "
            "Nie zgłasza niczego do IRZplus — zgłoszenie jest osobną czynnością."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "numer_kolczyka": _EAR_TAG_FIELD,
                "plec": {"type": "string", "enum": list(SEX_PL), "description": "Płeć zwierzęcia."},
                "data_urodzenia": _DATE_FIELD,
                "rasa": {"type": "string", "description": "Rasa, np. HO (holsztyńsko-fryzyjska)."},
                "imie": {"type": "string", "description": "Nazwa własna nadana przez hodowcę."},
                "numer_kolczyka_matki": {
                    "type": "string",
                    "description": "Numer kolczyka matki (PL + 12 cyfr), jeśli znany.",
                },
                "numer_siedziby_stada": {
                    "type": "string",
                    "description": "Numer siedziby stada w formacie PL + 9 cyfr.",
                },
            },
            "required": ["numer_kolczyka", "plec", "data_urodzenia"],
        },
    },
    {
        "name": "dodaj_zdarzenie",
        "description": (
            "Zapisuje zdarzenie zwierzęcia (urodzenie, przybycie, ubycie, padnięcie) w ewidencji. "
            "Wymaga potwierdzenia użytkownika. Zdarzenie trafia do kolejki ze statusem "
            "„niezgłoszone” — wysyłkę do IRZplus użytkownik uruchamia sam w aplikacji."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "numer_kolczyka": _EAR_TAG_FIELD,
                "typ": {"type": "string", "enum": list(EVENT_PL), "description": "Rodzaj zdarzenia."},
                "data": _DATE_FIELD,
                "numer_siedziby_partnera": {
                    "type": "string",
                    "description": (
                        "Siedziba, z której zwierzę przybyło lub do której ubyło (PL + 9 cyfr). "
                        "Wymagana dla przybycia i ubycia."
                    ),
                },
                "notatki": {"type": "string", "description": "Dodatkowy opis zdarzenia."},
            },
            "required": ["numer_kolczyka", "typ", "data"],
        },
    },
    {
        "name": "dodaj_leczenie",
        "description": (
            "Zapisuje zabieg leczniczy w dokumentacji zwierzęcia. Wymaga potwierdzenia użytkownika."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "numer_kolczyka": _EAR_TAG_FIELD,
                "lek": {"type": "string", "description": "Nazwa podanego preparatu."},
                "data": _DATE_FIELD,
                "dawka": {"type": "string", "description": "Dawka, np. 20 ml."},
                "lekarz": {"type": "string", "description": "Lekarz weterynarii wykonujący zabieg."},
                "karencja_do": {
                    "type": "string",
                    "description": "Data końca okresu karencji (RRRR-MM-DD), jeśli lek ją wymusza.",
                },
                "notatki": {"type": "string", "description": "Dodatkowy opis zabiegu."},
            },
            "required": ["numer_kolczyka", "lek", "data"],
        },
    },
]

TOOL_DEFINITIONS = READ_TOOL_DEFINITIONS + WRITE_TOOL_DEFINITIONS
READ_TOOLS = {t["name"] for t in READ_TOOL_DEFINITIONS}
WRITE_TOOLS = {t["name"] for t in WRITE_TOOL_DEFINITIONS}


def _ear_tag(value: Any, field: str = "numer_kolczyka") -> str:
    tag = str(value or "").strip().upper()
    if not EAR_TAG_PATTERN.fullmatch(tag):
        raise ToolError(f"Pole {field}: „{value}” nie jest numerem w formacie PL + 12 cyfr.")
    return tag


def _holding(value: Any, field: str) -> str:
    number = str(value or "").strip().upper()
    if not HOLDING_PATTERN.fullmatch(number):
        raise ToolError(f"Pole {field}: „{value}” nie jest numerem siedziby stada (PL + 9 cyfr).")
    return number


def _parse_date(value: Any, field: str) -> date:
    try:
        return date.fromisoformat(str(value).strip())
    except (TypeError, ValueError):
        raise ToolError(f"Pole {field}: „{value}” nie jest datą w formacie RRRR-MM-DD.") from None


def _require_animal(db: Session, ear_tag: str) -> models.Animal:
    animal = crud.get_animal_by_ear_tag(db, ear_tag)
    if animal is None:
        raise ToolError(
            f"W ewidencji nie ma zwierzęcia o numerze {ear_tag}. "
            "Najpierw dodaj sztukę albo sprawdź numer narzędziem lista_zwierzat."
        )
    return animal


def _animal_summary(animal: models.Animal) -> dict[str, Any]:
    return {
        "id": animal.id,
        "numer_kolczyka": animal.ear_tag,
        "imie": animal.name,
        "plec": SEX_LABEL.get(animal.sex, animal.sex.value),
        "data_urodzenia": animal.birth_date.isoformat() if animal.birth_date else None,
        "rasa": animal.breed,
        "numer_kolczyka_matki": animal.mother_ear_tag,
        "numer_siedziby_stada": animal.herd_id,
    }


def _event_summary(event: models.Event) -> dict[str, Any]:
    return {
        "id": event.id,
        "typ": EVENT_LABEL.get(event.type, event.type.value),
        "data": event.event_date.isoformat(),
        "status_irz": IRZ_LABEL.get(event.irz_status, event.irz_status.value),
        "numer_dokumentu_irz": event.irz_document_number,
        "komunikat_irz": event.irz_message,
        "numer_siedziby_partnera": event.partner_holding_number,
        "notatki": event.notes,
    }


def _treatment_summary(treatment: models.Treatment) -> dict[str, Any]:
    return {
        "id": treatment.id,
        "lek": treatment.drug,
        "dawka": treatment.dose,
        "lekarz": treatment.vet,
        "data": treatment.treatment_date.isoformat(),
        "karencja_do": treatment.withdrawal_until.isoformat() if treatment.withdrawal_until else None,
        "notatki": treatment.notes,
    }


def _last_event(animal: models.Animal) -> models.Event | None:
    """Zwraca ostatnie zdarzenie zwierzęcia z pominięciem zgłoszeń odrzuconych przez IRZplus.

    Warunek jest zgodny z ``HerdStatistics.isInHerd`` w aplikacji mobilnej.
    """
    actual = [e for e in animal.events if e.irz_status != models.IrzStatus.REJECTED]
    if not actual:
        return None
    return max(actual, key=lambda e: (e.event_date, e.id))


def _in_herd(animal: models.Animal) -> bool:
    """Określa, czy zwierzę jest w stadzie (ostatnie zdarzenie nie jest ubyciem ani padnięciem)."""
    last = _last_event(animal)
    return last is None or last.type not in (models.EventType.DEPARTURE, models.EventType.DEATH)


def _age_group(birth_date: date | None, sex: models.Sex, today: date) -> str:
    """Zwraca grupę wiekowo-płciową zwierzęcia, zgodną z podziałem w statystykach aplikacji.

    Powyżej wieku cielęcego kategorię użytkową określa płeć.
    """
    if birth_date is None:
        return "bez daty urodzenia"

    months = (today.year - birth_date.year) * 12 + today.month - birth_date.month
    if today.day < birth_date.day:
        months -= 1

    if months < 6:
        return "cielęta (do 6 mies.)"

    adult = months >= 24
    if sex == models.Sex.FEMALE:
        return "krowy (od 24 mies.)" if adult else "jałówki (6-24 mies.)"
    if sex == models.Sex.MALE:
        return "buhaje (od 24 mies.)" if adult else "byczki (6-24 mies.)"
    return "płeć nieokreślona"


def _lista_zwierzat(db: Session, params: dict[str, Any], today: date) -> dict[str, Any]:
    limit = params.get("limit") or 50
    try:
        limit = max(1, min(int(limit), 200))
    except (TypeError, ValueError):
        limit = 50
    szukaj = str(params.get("szukaj") or "").strip().lower()

    animals = crud.list_animals(db, limit=1000)
    if szukaj:
        animals = [
            a
            for a in animals
            if szukaj in a.ear_tag.lower()
            or szukaj in (a.name or "").lower()
            or szukaj in (a.breed or "").lower()
        ]
    wynik = []
    for animal in animals[:limit]:
        row = _animal_summary(animal)
        row["w_stadzie"] = _in_herd(animal)
        wynik.append(row)
    return {"liczba_dopasowan": len(animals), "zwierzeta": wynik}


def _dane_zwierzecia(db: Session, params: dict[str, Any], today: date) -> dict[str, Any]:
    animal = _require_animal(db, _ear_tag(params.get("numer_kolczyka")))
    return {
        "zwierze": _animal_summary(animal),
        "w_stadzie": _in_herd(animal),
        "zdarzenia": [_event_summary(e) for e in crud.list_events(db, animal.id)],
        "leczenia": [_treatment_summary(t) for t in crud.list_treatments(db, animal.id)],
    }


def _statystyki_stada(db: Session, params: dict[str, Any], today: date) -> dict[str, Any]:
    animals = crud.list_animals(db, limit=10000)
    w_stadzie = [a for a in animals if _in_herd(a)]

    plec: dict[str, int] = {}
    wiek: dict[str, int] = {}
    rasy: dict[str, int] = {}
    for animal in w_stadzie:
        plec_label = SEX_LABEL.get(animal.sex, animal.sex.value)
        plec[plec_label] = plec.get(plec_label, 0) + 1
        grupa = _age_group(animal.birth_date, animal.sex, today)
        wiek[grupa] = wiek.get(grupa, 0) + 1
        rasa = animal.breed or "brak rasy"
        rasy[rasa] = rasy.get(rasa, 0) + 1

    do_zgloszenia = _zdarzenia_do_zgloszenia(db, {}, today)
    karencje = _aktywne_karencje(db, {}, today)
    return {
        "dzisiaj": today.isoformat(),
        "liczba_w_ewidencji": len(animals),
        "liczba_w_stadzie": len(w_stadzie),
        "wg_plci": plec,
        "wg_wieku": wiek,
        "wg_rasy": rasy,
        "zdarzenia_do_zgloszenia": do_zgloszenia["liczba"],
        "zdarzenia_po_terminie": do_zgloszenia["liczba_po_terminie"],
        "aktywne_karencje": karencje["liczba"],
    }


def _zdarzenia_do_zgloszenia(db: Session, params: dict[str, Any], today: date) -> dict[str, Any]:
    events = (
        db.query(models.Event)
        .filter(models.Event.irz_status != models.IrzStatus.CONFIRMED)
        .order_by(models.Event.event_date)
        .all()
    )
    pozycje = []
    for event in events:
        deadline = event.event_date + timedelta(days=REPORTING_DAYS)
        pozycje.append(
            {
                **_event_summary(event),
                "numer_kolczyka": event.animal.ear_tag,
                "termin_zgloszenia": deadline.isoformat(),
                "dni_do_terminu": (deadline - today).days,
                "po_terminie": deadline < today,
            }
        )
    return {
        "liczba": len(pozycje),
        "liczba_po_terminie": sum(1 for p in pozycje if p["po_terminie"]),
        "zdarzenia": pozycje,
    }


def _aktywne_karencje(db: Session, params: dict[str, Any], today: date) -> dict[str, Any]:
    treatments = (
        db.query(models.Treatment)
        .filter(models.Treatment.withdrawal_until.isnot(None))
        .filter(models.Treatment.withdrawal_until >= today)
        .order_by(models.Treatment.withdrawal_until)
        .all()
    )
    pozycje = [
        {
            **_treatment_summary(t),
            "numer_kolczyka": t.animal.ear_tag,
            "dni_do_konca_karencji": (t.withdrawal_until - today).days,
        }
        for t in treatments
    ]
    return {"liczba": len(pozycje), "karencje": pozycje}


def _dodaj_zwierze(db: Session, params: dict[str, Any], today: date) -> dict[str, Any]:
    ear_tag = _ear_tag(params.get("numer_kolczyka"))
    if crud.get_animal_by_ear_tag(db, ear_tag) is not None:
        raise ToolError(f"Zwierzę o numerze {ear_tag} jest już w ewidencji.")

    plec = str(params.get("plec") or "").strip().lower()
    if plec not in SEX_PL:
        raise ToolError(f"Pole plec: dopuszczalne wartości to {', '.join(SEX_PL)}.")

    birth_date = _parse_date(params.get("data_urodzenia"), "data_urodzenia")
    if birth_date > today:
        raise ToolError("Data urodzenia nie może być z przyszłości.")

    mother = params.get("numer_kolczyka_matki")
    herd = params.get("numer_siedziby_stada")
    payload = schemas.AnimalCreate(
        ear_tag=ear_tag,
        name=params.get("imie") or None,
        birth_date=birth_date,
        breed=params.get("rasa") or None,
        sex=SEX_PL[plec],
        mother_ear_tag=_ear_tag(mother, "numer_kolczyka_matki") if mother else None,
        herd_id=_holding(herd, "numer_siedziby_stada") if herd else None,
    )
    animal = crud.create_animal(db, payload)
    return {"dodano": _animal_summary(animal)}


def _dodaj_zdarzenie(db: Session, params: dict[str, Any], today: date) -> dict[str, Any]:
    animal = _require_animal(db, _ear_tag(params.get("numer_kolczyka")))
    typ = str(params.get("typ") or "").strip().lower()
    if typ not in EVENT_PL:
        raise ToolError(f"Pole typ: dopuszczalne wartości to {', '.join(EVENT_PL)}.")

    event_date = _parse_date(params.get("data"), "data")
    if event_date > today:
        raise ToolError("Data zdarzenia nie może być z przyszłości.")
    if animal.birth_date and event_date < animal.birth_date:
        raise ToolError(
            f"Data zdarzenia ({event_date.isoformat()}) jest wcześniejsza niż data urodzenia "
            f"zwierzęcia ({animal.birth_date.isoformat()})."
        )

    event_type = EVENT_PL[typ]
    partner = params.get("numer_siedziby_partnera")
    if event_type in (models.EventType.ARRIVAL, models.EventType.DEPARTURE) and not partner:
        raise ToolError(
            "Dla przybycia i ubycia IRZplus wymaga numeru siedziby stada partnera "
            "(pole numer_siedziby_partnera, format PL + 9 cyfr). Zapytaj użytkownika o ten numer."
        )

    payload = schemas.EventCreate(
        animal_id=animal.id,
        type=event_type,
        event_date=event_date,
        notes=params.get("notatki") or None,
        partner_holding_number=_holding(partner, "numer_siedziby_partnera") if partner else None,
    )
    event = crud.create_event(db, payload)
    deadline = event.event_date + timedelta(days=REPORTING_DAYS)
    return {
        "dodano": {**_event_summary(event), "numer_kolczyka": animal.ear_tag},
        "termin_zgloszenia_do_irz": deadline.isoformat(),
        "uwaga": (
            "Zdarzenie zapisano w ewidencji. Zgłoszenie do IRZplus użytkownik wysyła sam "
            "z karty zwierzęcia w aplikacji."
        ),
    }


def _dodaj_leczenie(db: Session, params: dict[str, Any], today: date) -> dict[str, Any]:
    animal = _require_animal(db, _ear_tag(params.get("numer_kolczyka")))
    lek = str(params.get("lek") or "").strip()
    if not lek:
        raise ToolError("Pole lek jest wymagane.")

    treatment_date = _parse_date(params.get("data"), "data")
    if treatment_date > today:
        raise ToolError("Data zabiegu nie może być z przyszłości.")

    karencja = params.get("karencja_do")
    withdrawal = _parse_date(karencja, "karencja_do") if karencja else None
    if withdrawal and withdrawal < treatment_date:
        raise ToolError("Koniec karencji nie może wypadać przed datą zabiegu.")

    payload = schemas.TreatmentCreate(
        animal_id=animal.id,
        drug=lek,
        dose=params.get("dawka") or None,
        vet=params.get("lekarz") or None,
        treatment_date=treatment_date,
        withdrawal_until=withdrawal,
        notes=params.get("notatki") or None,
    )
    treatment = crud.create_treatment(db, payload)
    return {"dodano": {**_treatment_summary(treatment), "numer_kolczyka": animal.ear_tag}}


_EXECUTORS = {
    "lista_zwierzat": _lista_zwierzat,
    "dane_zwierzecia": _dane_zwierzecia,
    "statystyki_stada": _statystyki_stada,
    "zdarzenia_do_zgloszenia": _zdarzenia_do_zgloszenia,
    "aktywne_karencje": _aktywne_karencje,
    "dodaj_zwierze": _dodaj_zwierze,
    "dodaj_zdarzenie": _dodaj_zdarzenie,
    "dodaj_leczenie": _dodaj_leczenie,
}


def execute_tool(
    db: Session, name: str, params: dict[str, Any], today: date | None = None
) -> dict[str, Any]:
    """Wykonuje narzędzie i zwraca słownik z wynikiem; błędy sygnalizuje wyjątkiem ``ToolError``."""
    executor = _EXECUTORS.get(name)
    if executor is None:
        raise ToolError(f"Nieznane narzędzie: {name}.")
    return executor(db, params or {}, today or datetime.now().date())


def describe_action(name: str, params: dict[str, Any]) -> str:
    """Zwraca opis akcji prezentowany użytkownikowi w oknie potwierdzenia."""
    params = params or {}
    tag = params.get("numer_kolczyka", "?")
    if name == "dodaj_zwierze":
        opis = f"Dodać do ewidencji zwierzę {tag} ({params.get('plec', '?')}"
        if params.get("data_urodzenia"):
            opis += f", ur. {params['data_urodzenia']}"
        if params.get("rasa"):
            opis += f", rasa {params['rasa']}"
        return opis + ")?"
    if name == "dodaj_zdarzenie":
        opis = (
            f"Zapisać zdarzenie „{params.get('typ', '?')}” dla {tag} "
            f"z dnia {params.get('data', '?')}"
        )
        if params.get("numer_siedziby_partnera"):
            opis += f" (siedziba partnera {params['numer_siedziby_partnera']})"
        return opis + "?"
    if name == "dodaj_leczenie":
        opis = (
            f"Zapisać leczenie zwierzęcia {tag}: {params.get('lek', '?')} "
            f"z dnia {params.get('data', '?')}"
        )
        if params.get("karencja_do"):
            opis += f", karencja do {params['karencja_do']}"
        return opis + "?"
    return f"Wykonać akcję {name} z danymi {params}?"
