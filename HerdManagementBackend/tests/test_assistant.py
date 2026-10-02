"""Testy asystenta LLM (Zadanie 8).

Model jest podmieniony na atrapę (``FakeLlm``) odtwarzającą kolejne odpowiedzi
Messages API, więc testy nie wychodzą do sieci i nie kosztują tokenów.
Sprawdzamy to, co jest logiką backendu: wykonywanie narzędzi, politykę
potwierdzania akcji zapisujących i kontrakt REST.
"""
from datetime import date, timedelta
from typing import Any

import pytest
from fastapi.testclient import TestClient

from app import crud, models, schemas
from app.assistant import tools
from app.assistant.agent import Assistant
from app.assistant.conversation import ConversationStore
from app.assistant.router import get_assistant
from app.database import get_db
from main import app

TODAY = date(2026, 9, 21)


# --- Atrapa modelu ------------------------------------------------------

class FakeResponse:
    def __init__(self, content: list[dict[str, Any]], stop_reason: str) -> None:
        self.content = content
        self.stop_reason = stop_reason


class FakeLlm:
    """Odtwarza zaplanowane odpowiedzi modelu i zapamiętuje wysłane żądania."""

    def __init__(self, responses: list[FakeResponse]) -> None:
        self.responses = list(responses)
        self.requests: list[dict[str, Any]] = []

    def create_message(self, *, system, messages, tools):  # noqa: A002 - nazwa z kontraktu
        self.requests.append({"system": system, "messages": list(messages), "tools": tools})
        if not self.responses:
            raise AssertionError("Atrapa modelu nie ma więcej zaplanowanych odpowiedzi.")
        return self.responses.pop(0)


def text_response(text: str) -> FakeResponse:
    return FakeResponse([{"type": "text", "text": text}], "end_turn")


def tool_response(name: str, params: dict[str, Any], tool_use_id: str = "tu_1", text: str = "") -> FakeResponse:
    content: list[dict[str, Any]] = []
    if text:
        content.append({"type": "text", "text": text})
    content.append({"type": "tool_use", "id": tool_use_id, "name": name, "input": params})
    return FakeResponse(content, "tool_use")


# --- Fikstury -----------------------------------------------------------

@pytest.fixture
def db(client: TestClient):
    """Sesja do bazy testowej — ta sama, z której korzystają endpointy."""
    generator = app.dependency_overrides[get_db]()
    session = next(generator)
    try:
        yield session
    finally:
        generator.close()


@pytest.fixture(autouse=True)
def fresh_conversations(monkeypatch: pytest.MonkeyPatch) -> ConversationStore:
    """Świeży rejestr rozmów dla każdego testu (izolacja stanu w pamięci)."""
    fresh = ConversationStore()
    monkeypatch.setattr("app.assistant.conversation.store", fresh)
    monkeypatch.setattr("app.assistant.agent.store", fresh)
    monkeypatch.setattr("app.assistant.router.store", fresh)
    return fresh


def seed_animal(db, ear_tag: str = "PL005338887002", birth: date = date(2023, 4, 1)) -> models.Animal:
    return crud.create_animal(
        db,
        schemas.AnimalCreate(
            ear_tag=ear_tag,
            name="Krasula",
            birth_date=birth,
            breed="HO",
            sex=models.Sex.FEMALE,
            herd_id="PL000111222",
        ),
    )


# --- Narzędzia odczytowe ------------------------------------------------

def test_lista_zwierzat_filtruje_po_fragmencie(db):
    seed_animal(db)
    seed_animal(db, "PL005338887015")

    wynik = tools.execute_tool(db, "lista_zwierzat", {"szukaj": "7015"}, TODAY)

    assert wynik["liczba_dopasowan"] == 1
    assert wynik["zwierzeta"][0]["numer_kolczyka"] == "PL005338887015"


def test_dane_zwierzecia_zwraca_zdarzenia_i_leczenia(db):
    animal = seed_animal(db)
    crud.create_event(
        db,
        schemas.EventCreate(animal_id=animal.id, type=models.EventType.BIRTH, event_date=date(2023, 4, 1)),
    )
    crud.create_treatment(
        db,
        schemas.TreatmentCreate(
            animal_id=animal.id,
            drug="Penicylina",
            treatment_date=TODAY - timedelta(days=2),
            withdrawal_until=TODAY + timedelta(days=3),
        ),
    )

    wynik = tools.execute_tool(db, "dane_zwierzecia", {"numer_kolczyka": animal.ear_tag}, TODAY)

    assert wynik["zwierze"]["plec"] == "samica"
    assert wynik["zdarzenia"][0]["typ"] == "urodzenie"
    assert wynik["zdarzenia"][0]["status_irz"] == "niezgłoszone"
    assert wynik["leczenia"][0]["lek"] == "Penicylina"


def test_dane_zwierzecia_nieznany_numer_to_blad(db):
    with pytest.raises(tools.ToolError, match="nie ma zwierzęcia"):
        tools.execute_tool(db, "dane_zwierzecia", {"numer_kolczyka": "PL999999999999"}, TODAY)


def test_zdarzenia_do_zgloszenia_liczy_termin_7_dni(db):
    animal = seed_animal(db)
    crud.create_event(
        db,
        schemas.EventCreate(
            animal_id=animal.id, type=models.EventType.BIRTH, event_date=TODAY - timedelta(days=10)
        ),
    )

    wynik = tools.execute_tool(db, "zdarzenia_do_zgloszenia", {}, TODAY)

    assert wynik["liczba"] == 1
    pozycja = wynik["zdarzenia"][0]
    assert pozycja["po_terminie"] is True
    assert pozycja["dni_do_terminu"] == -3
    assert pozycja["numer_kolczyka"] == animal.ear_tag


def test_statystyki_pomijaja_sztuki_ktore_ubyly(db):
    zostaje = seed_animal(db)
    ubywa = seed_animal(db, "PL005338887015")
    crud.create_event(
        db,
        schemas.EventCreate(
            animal_id=ubywa.id, type=models.EventType.DEPARTURE, event_date=TODAY - timedelta(days=1),
            partner_holding_number="PL000999888",
        ),
    )

    wynik = tools.execute_tool(db, "statystyki_stada", {}, TODAY)

    assert wynik["liczba_w_ewidencji"] == 2
    assert wynik["liczba_w_stadzie"] == 1
    assert wynik["wg_plci"] == {"samica": 1}
    assert zostaje.ear_tag  # sztuka pozostająca w stadzie


def test_aktywne_karencje_pomijaja_zakonczone(db):
    animal = seed_animal(db)
    crud.create_treatment(
        db,
        schemas.TreatmentCreate(
            animal_id=animal.id, drug="Stara kuracja", treatment_date=TODAY - timedelta(days=30),
            withdrawal_until=TODAY - timedelta(days=20),
        ),
    )
    crud.create_treatment(
        db,
        schemas.TreatmentCreate(
            animal_id=animal.id, drug="Bieżąca kuracja", treatment_date=TODAY,
            withdrawal_until=TODAY + timedelta(days=5),
        ),
    )

    wynik = tools.execute_tool(db, "aktywne_karencje", {}, TODAY)

    assert wynik["liczba"] == 1
    assert wynik["karencje"][0]["lek"] == "Bieżąca kuracja"
    assert wynik["karencje"][0]["dni_do_konca_karencji"] == 5


# --- Walidacja narzędzi zapisujących ------------------------------------

def test_dodaj_zwierze_odrzuca_zly_numer(db):
    with pytest.raises(tools.ToolError, match="PL \\+ 12 cyfr"):
        tools.execute_tool(
            db, "dodaj_zwierze", {"numer_kolczyka": "123", "plec": "samica", "data_urodzenia": "2026-01-01"}, TODAY
        )


def test_dodaj_zwierze_odrzuca_duplikat(db):
    animal = seed_animal(db)
    with pytest.raises(tools.ToolError, match="już w ewidencji"):
        tools.execute_tool(
            db,
            "dodaj_zwierze",
            {"numer_kolczyka": animal.ear_tag, "plec": "samica", "data_urodzenia": "2024-01-01"},
            TODAY,
        )


def test_dodaj_zdarzenie_wymaga_siedziby_partnera(db):
    animal = seed_animal(db)
    with pytest.raises(tools.ToolError, match="numer_siedziby_partnera"):
        tools.execute_tool(
            db,
            "dodaj_zdarzenie",
            {"numer_kolczyka": animal.ear_tag, "typ": "przybycie", "data": TODAY.isoformat()},
            TODAY,
        )


def test_dodaj_zdarzenie_odrzuca_date_przed_urodzeniem(db):
    animal = seed_animal(db, birth=date(2025, 1, 1))
    with pytest.raises(tools.ToolError, match="wcześniejsza niż data urodzenia"):
        tools.execute_tool(
            db,
            "dodaj_zdarzenie",
            {"numer_kolczyka": animal.ear_tag, "typ": "padniecie", "data": "2024-12-31"},
            TODAY,
        )


def test_dodaj_leczenie_zapisuje_karencje(db):
    animal = seed_animal(db)
    wynik = tools.execute_tool(
        db,
        "dodaj_leczenie",
        {
            "numer_kolczyka": animal.ear_tag,
            "lek": "Amoksycylina",
            "data": TODAY.isoformat(),
            "dawka": "20 ml",
            "karencja_do": (TODAY + timedelta(days=7)).isoformat(),
        },
        TODAY,
    )

    assert wynik["dodano"]["karencja_do"] == (TODAY + timedelta(days=7)).isoformat()
    assert len(crud.list_treatments(db, animal.id)) == 1


# --- Pętla agentowa i polityka potwierdzania ----------------------------

def test_narzedzie_odczytowe_wykonuje_sie_bez_pytania(db, fresh_conversations):
    seed_animal(db)
    llm = FakeLlm([
        tool_response("lista_zwierzat", {}),
        text_response("W stadzie jest 1 sztuka."),
    ])
    assistant = Assistant(llm, today=TODAY)
    conversation = fresh_conversations.create()

    wynik = assistant.send(db, conversation, "Ile mam zwierząt?")

    assert wynik.pending is None
    assert wynik.reply == "W stadzie jest 1 sztuka."
    assert conversation.pending is None
    # wynik narzędzia wrócił do modelu jako tool_result
    ostatnie_zadanie = llm.requests[-1]["messages"]
    assert ostatnie_zadanie[-1]["content"][0]["type"] == "tool_result"


def test_akcja_zapisujaca_czeka_na_potwierdzenie(db, fresh_conversations):
    seed_animal(db)
    params = {"numer_kolczyka": "PL005338887015", "plec": "samiec", "data_urodzenia": "2026-09-20"}
    llm = FakeLlm([tool_response("dodaj_zwierze", params, text="Przygotowałem wpis.")])
    assistant = Assistant(llm, today=TODAY)
    conversation = fresh_conversations.create()

    wynik = assistant.send(db, conversation, "Dodaj byczka PL005338887015 ur. wczoraj")

    assert wynik.pending is not None
    assert wynik.pending.name == "dodaj_zwierze"
    assert "PL005338887015" in wynik.pending.description
    # nic nie trafiło do bazy przed decyzją użytkownika
    assert crud.get_animal_by_ear_tag(db, "PL005338887015") is None


def test_potwierdzenie_wykonuje_akcje_i_wraca_do_modelu(db, fresh_conversations):
    params = {"numer_kolczyka": "PL005338887015", "plec": "samiec", "data_urodzenia": "2026-09-20"}
    llm = FakeLlm([
        tool_response("dodaj_zwierze", params),
        text_response("Dodałem byczka do ewidencji."),
    ])
    assistant = Assistant(llm, today=TODAY)
    conversation = fresh_conversations.create()
    assistant.send(db, conversation, "Dodaj byczka")

    wynik = assistant.resolve_pending(db, conversation, approved=True)

    assert wynik.reply == "Dodałem byczka do ewidencji."
    assert crud.get_animal_by_ear_tag(db, "PL005338887015") is not None
    assert conversation.pending is None
    assert [a.ok for a in wynik.executed] == [True]
    assert fresh_conversations.audit()[-1].approved is True


def test_odrzucenie_nie_zapisuje_niczego(db, fresh_conversations):
    params = {"numer_kolczyka": "PL005338887015", "plec": "samiec", "data_urodzenia": "2026-09-20"}
    llm = FakeLlm([
        tool_response("dodaj_zwierze", params),
        text_response("Rozumiem, nic nie zapisuję."),
    ])
    assistant = Assistant(llm, today=TODAY)
    conversation = fresh_conversations.create()
    assistant.send(db, conversation, "Dodaj byczka")

    wynik = assistant.resolve_pending(db, conversation, approved=False, comment="zła data")

    assert crud.get_animal_by_ear_tag(db, "PL005338887015") is None
    assert wynik.executed[0].approved is False
    # model dostał informację o odmowie i komentarz użytkownika
    ostatnia_wiadomosc = llm.requests[-1]["messages"][-1]["content"]
    assert ostatnia_wiadomosc[0]["type"] == "tool_result"
    assert "zła data" in ostatnia_wiadomosc[-1]["text"]


def test_blad_narzedzia_wraca_do_modelu_jako_is_error(db, fresh_conversations):
    llm = FakeLlm([
        tool_response("dane_zwierzecia", {"numer_kolczyka": "PL999999999999"}),
        text_response("Nie znalazłem takiej sztuki."),
    ])
    assistant = Assistant(llm, today=TODAY)
    conversation = fresh_conversations.create()

    wynik = assistant.send(db, conversation, "Pokaż PL999999999999")

    assert wynik.reply == "Nie znalazłem takiej sztuki."
    tool_result = llm.requests[-1]["messages"][-1]["content"][0]
    assert tool_result["is_error"] is True


def test_nieudany_zapis_po_potwierdzeniu_jest_raportowany(db, fresh_conversations):
    animal = seed_animal(db)
    params = {"numer_kolczyka": animal.ear_tag, "plec": "samica", "data_urodzenia": "2024-01-01"}
    llm = FakeLlm([
        tool_response("dodaj_zwierze", params),
        text_response("Ta sztuka już jest w ewidencji."),
    ])
    assistant = Assistant(llm, today=TODAY)
    conversation = fresh_conversations.create()
    assistant.send(db, conversation, "Dodaj tę krowę")

    wynik = assistant.resolve_pending(db, conversation, approved=True)

    assert wynik.executed[0].ok is False
    assert "już w ewidencji" in wynik.executed[0].summary


def test_druga_wiadomosc_przed_decyzja_jest_blokowana(db, fresh_conversations):
    params = {"numer_kolczyka": "PL005338887015", "plec": "samiec", "data_urodzenia": "2026-09-20"}
    llm = FakeLlm([tool_response("dodaj_zwierze", params)])
    assistant = Assistant(llm, today=TODAY)
    conversation = fresh_conversations.create()
    assistant.send(db, conversation, "Dodaj byczka")

    with pytest.raises(Exception, match="czeka na decyzję"):
        assistant.send(db, conversation, "A przy okazji ile mam krów?")


# --- Kontrakt REST ------------------------------------------------------

@pytest.fixture
def fake_llm_app(client: TestClient):
    """Podpina atrapę modelu pod endpointy; test wypełnia listę odpowiedzi."""
    llm = FakeLlm([])
    app.dependency_overrides[get_assistant] = lambda: Assistant(llm, today=TODAY)
    yield llm
    app.dependency_overrides.pop(get_assistant, None)


def test_endpoint_chat_i_confirm(client: TestClient, fake_llm_app: FakeLlm):
    client.post(
        "/animals",
        json={"ear_tag": "PL005338887002", "sex": "female", "birth_date": "2023-04-01"},
    )
    params = {"numer_kolczyka": "PL005338887002", "typ": "urodzenie", "data": "2023-04-01"}
    fake_llm_app.responses = [
        tool_response("dodaj_zdarzenie", params, text="Mam komplet danych."),
        text_response("Zapisałem zdarzenie. Zgłoś je jeszcze do IRZplus z karty zwierzęcia."),
    ]

    chat = client.post("/assistant/chat", json={"message": "Zapisz urodzenie tej krowy"})
    assert chat.status_code == 200
    body = chat.json()
    assert body["state"] == "awaiting_confirmation"
    assert body["pending_action"]["name"] == "dodaj_zdarzenie"
    conversation_id = body["conversation_id"]

    confirm = client.post(
        "/assistant/confirm", json={"conversation_id": conversation_id, "approved": True}
    )
    assert confirm.status_code == 200
    potwierdzenie = confirm.json()
    assert potwierdzenie["state"] == "answer"
    assert potwierdzenie["executed_actions"][0]["ok"] is True

    events = client.get("/animals/1/events").json()
    assert len(events) == 1
    assert events[0]["type"] == "birth"

    historia = client.get(f"/assistant/conversations/{conversation_id}").json()
    assert historia[0] == {"role": "user", "text": "Zapisz urodzenie tej krowy"}
    assert historia[-1]["role"] == "assistant"

    audit = client.get("/assistant/audit").json()
    assert audit[-1]["name"] == "dodaj_zdarzenie"
    assert audit[-1]["approved"] is True


def test_endpoint_chat_nieznana_rozmowa_to_404(client: TestClient, fake_llm_app: FakeLlm):
    response = client.post("/assistant/chat", json={"message": "cześć", "conversation_id": "brak"})
    assert response.status_code == 404


def test_endpoint_confirm_bez_oczekujacej_akcji_to_409(client: TestClient, fake_llm_app: FakeLlm):
    fake_llm_app.responses = [text_response("Dzień dobry, w czym mogę pomóc?")]
    chat = client.post("/assistant/chat", json={"message": "cześć"}).json()

    response = client.post(
        "/assistant/confirm", json={"conversation_id": chat["conversation_id"], "approved": True}
    )
    assert response.status_code == 409


def test_status_bez_klucza_zglasza_brak_konfiguracji(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr("app.assistant.llm.api_key", lambda: "")
    response = client.get("/assistant/status")
    assert response.status_code == 200
    assert response.json()["configured"] is False
    assert "lista_zwierzat" in response.json()["tools"]


def test_chat_bez_klucza_zwraca_503(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr("app.assistant.llm.api_key", lambda: "")
    response = client.post("/assistant/chat", json={"message": "cześć"})
    assert response.status_code == 503


def test_grupy_uzytkowe_rozrozniaja_plec_powyzej_wieku_cielecego():
    """Spójność z ekranem statystyk: roczny samiec to byczek, nie „młodzież”.

    Asystent i statystyki opisują to samo stado, więc muszą używać tego samego
    podziału — inaczej te same zwierzęta liczą się inaczej w dwóch miejscach.
    """
    from datetime import date

    from app.assistant.tools import _age_group
    from app.models import Sex

    today = date(2026, 9, 22)
    roczny = date(2025, 8, 22)

    assert _age_group(roczny, Sex.MALE, today) == "byczki (6-24 mies.)"
    assert _age_group(roczny, Sex.FEMALE, today) == "jałówki (6-24 mies.)"
    assert _age_group(date(2026, 6, 22), Sex.MALE, today) == "cielęta (do 6 mies.)"
    assert _age_group(date(2020, 1, 1), Sex.MALE, today) == "buhaje (od 24 mies.)"
    assert _age_group(date(2020, 1, 1), Sex.FEMALE, today) == "krowy (od 24 mies.)"
    assert _age_group(None, Sex.FEMALE, today) == "bez daty urodzenia"


def test_odrzucone_ubycie_nie_wyprowadza_sztuki_ze_stada(db):
    """Zgłoszenie odrzucone przez rejestr nie zmienia obrazu stada.

    Hodowca może zgłosić ubycie dwa razy — drugie odbije się od IRZplus, bo sztuka
    jest już tam rozliczona. Dopóki `_last_event` liczyło także odrzucone, asystent
    meldował, że żywa sztuka opuściła gospodarstwo, i rozchodził się ze statystykami
    w aplikacji (`HerdStatistics.isInHerd` stosuje ten sam warunek).
    """
    animal = seed_animal(db)
    event = crud.create_event(
        db,
        schemas.EventCreate(
            animal_id=animal.id,
            type=models.EventType.DEPARTURE,
            event_date=TODAY - timedelta(days=1),
            partner_holding_number="PL000999888",
        ),
    )
    event.irz_status = models.IrzStatus.REJECTED
    db.commit()

    wynik = tools.execute_tool(db, "statystyki_stada", {}, TODAY)
    assert wynik["liczba_w_stadzie"] == 1

    dane = tools.execute_tool(
        db, "dane_zwierzecia", {"numer_kolczyka": animal.ear_tag}, TODAY
    )
    assert dane["w_stadzie"] is True
    # Samo zdarzenie zostaje w historii — hodowca musi widzieć, że próba się nie udała.
    assert len(dane["zdarzenia"]) == 1
