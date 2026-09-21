"""Testy synchronizacji przyrostowej (GET /sync/changes).

Scenariusz, który te testy pilnują: zmiana powstała po stronie backendu (np. wpis
asystenta LLM albo dane z innego urządzenia) musi dać się pobrać na telefon, a
usunięty rekord musi zostać zgłoszony jako usunięty — inaczej ewidencja na
telefonie po cichu rozjeżdża się z serwerem.
"""
from fastapi.testclient import TestClient

ANIMAL = {
    "ear_tag": "PL004512378901",
    "name": "Krasula",
    "birth_date": "2021-03-14",
    "breed": "Holsztyńsko-fryzyjska",
    "sex": "female",
    "herd_id": "PL000111222",
}


def _create_animal(client: TestClient, ear_tag: str = ANIMAL["ear_tag"]) -> dict:
    response = client.post("/animals", json={**ANIMAL, "ear_tag": ear_tag})
    assert response.status_code == 201, response.text
    return response.json()


def _changes(client: TestClient, since: str | None = None) -> dict:
    params = {"since": since} if since else None
    response = client.get("/sync/changes", params=params)
    assert response.status_code == 200, response.text
    return response.json()


def test_pusty_since_zwraca_pelna_migawke(client: TestClient):
    _create_animal(client)
    data = _changes(client)

    assert data["since"] is None
    assert [a["ear_tag"] for a in data["animals"]] == [ANIMAL["ear_tag"]]
    assert data["server_time"]


def test_nowe_zwierze_trafia_do_zmian_po_znaczniku(client: TestClient):
    first = _changes(client)
    assert first["animals"] == []

    _create_animal(client)
    data = _changes(client, since=first["server_time"])

    assert len(data["animals"]) == 1
    assert data["animals"][0]["ear_tag"] == ANIMAL["ear_tag"]


def test_niezmienione_rekordy_nie_wracaja_drugi_raz(client: TestClient):
    _create_animal(client)
    first = _changes(client)
    assert len(first["animals"]) == 1

    # Nic się nie zmieniło — druga synchronizacja ma nie przesyłać tego samego.
    second = _changes(client, since=first["server_time"])
    assert second["animals"] == []
    assert second["events"] == []
    assert second["treatments"] == []
    assert second["deletions"] == []


def test_edycja_zwierzecia_odswieza_updated_at(client: TestClient):
    animal = _create_animal(client)
    checkpoint = _changes(client)["server_time"]

    response = client.put(f"/animals/{animal['id']}", json={**ANIMAL, "name": "Krasula II"})
    assert response.status_code == 200

    data = _changes(client, since=checkpoint)
    assert len(data["animals"]) == 1
    assert data["animals"][0]["name"] == "Krasula II"
    assert data["animals"][0]["updated_at"] is not None


def test_zdarzenie_i_leczenie_sa_synchronizowane(client: TestClient):
    animal = _create_animal(client)
    checkpoint = _changes(client)["server_time"]

    event = client.post(
        f"/animals/{animal['id']}/events",
        json={"animal_id": animal["id"], "type": "birth", "event_date": "2024-05-01"},
    )
    assert event.status_code == 201, event.text
    treatment = client.post(
        f"/animals/{animal['id']}/treatments",
        json={"animal_id": animal["id"], "drug": "Ketoprofen", "treatment_date": "2024-05-02"},
    )
    assert treatment.status_code == 201, treatment.text

    data = _changes(client, since=checkpoint)
    assert len(data["events"]) == 1
    assert len(data["treatments"]) == 1
    assert data["animals"] == []  # samo zwierzę się nie zmieniło


def test_patch_irz_wypycha_zdarzenie_do_zmian(client: TestClient):
    """Wynik zgłoszenia do IRZplus też musi dotrzeć na pozostałe urządzenia."""
    animal = _create_animal(client)
    event = client.post(
        f"/animals/{animal['id']}/events",
        json={"animal_id": animal["id"], "type": "birth", "event_date": "2024-05-01"},
    ).json()
    checkpoint = _changes(client)["server_time"]

    response = client.patch(
        f"/events/{event['id']}/irz",
        json={"irz_status": "confirmed", "irz_document_number": "ZGL/2024/00001"},
    )
    assert response.status_code == 200, response.text

    data = _changes(client, since=checkpoint)
    assert len(data["events"]) == 1
    assert data["events"][0]["irz_status"] == "confirmed"
    assert data["events"][0]["irz_document_number"] == "ZGL/2024/00001"


def test_usuniecie_zwierzecia_zglasza_nagrobki_dzieci(client: TestClient):
    animal = _create_animal(client)
    event = client.post(
        f"/animals/{animal['id']}/events",
        json={"animal_id": animal["id"], "type": "birth", "event_date": "2024-05-01"},
    ).json()
    treatment = client.post(
        f"/animals/{animal['id']}/treatments",
        json={"animal_id": animal["id"], "drug": "Ketoprofen", "treatment_date": "2024-05-02"},
    ).json()
    checkpoint = _changes(client)["server_time"]

    assert client.delete(f"/animals/{animal['id']}").status_code == 204

    data = _changes(client, since=checkpoint)
    removed = {(d["entity"], d["entity_id"]) for d in data["deletions"]}
    assert ("animal", animal["id"]) in removed
    assert ("event", event["id"]) in removed
    assert ("treatment", treatment["id"]) in removed


def test_usuniecie_zdarzenia_zglasza_nagrobek(client: TestClient):
    animal = _create_animal(client)
    event = client.post(
        f"/animals/{animal['id']}/events",
        json={"animal_id": animal["id"], "type": "birth", "event_date": "2024-05-01"},
    ).json()
    checkpoint = _changes(client)["server_time"]

    assert client.delete(f"/events/{event['id']}").status_code == 204

    data = _changes(client, since=checkpoint)
    assert [(d["entity"], d["entity_id"]) for d in data["deletions"]] == [("event", event["id"])]


def test_stare_nagrobki_nie_wracaja(client: TestClient):
    animal = _create_animal(client)
    assert client.delete(f"/animals/{animal['id']}").status_code == 204
    first = _changes(client)
    assert first["deletions"]

    second = _changes(client, since=first["server_time"])
    assert second["deletions"] == []


def test_since_z_offsetem_strefowym_liczy_sie_jak_utc(client: TestClient):
    """Telefon w strefie +02:00 nie może dostać dwóch godzin zmian w kółko."""
    from datetime import datetime, timedelta, timezone

    _create_animal(client)
    utc_now = datetime.now(timezone.utc)
    warsaw = utc_now.astimezone(timezone(timedelta(hours=2)))

    data = _changes(client, since=warsaw.isoformat())
    assert data["animals"] == []  # ten sam moment, tylko zapisany inaczej


def test_wpis_asystenta_widoczny_w_synchronizacji(client: TestClient):
    """Narzędzie asystenta zapisuje prosto w bazie backendu — telefon musi to zobaczyć."""
    from datetime import date

    from app.assistant import tools
    from app.database import get_db
    from main import app

    animal = _create_animal(client)
    checkpoint = _changes(client)["server_time"]

    db = next(app.dependency_overrides[get_db]())
    tools.execute_tool(
        db,
        "dodaj_leczenie",
        {
            "numer_kolczyka": animal["ear_tag"],
            "lek": "Penicylina prokainowa",
            "data": date.today().isoformat(),
            "karencja_do": date.today().isoformat(),
        },
        date.today(),
    )

    data = _changes(client, since=checkpoint)
    assert len(data["treatments"]) == 1
    assert data["treatments"][0]["drug"] == "Penicylina prokainowa"
