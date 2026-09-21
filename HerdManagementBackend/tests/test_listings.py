"""Testy modułu publikacji ogłoszeń (zadanie 9).

Testy są zorganizowane wokół wymagań z dokumentacji:
F13 — formularz wstępnie wypełniony danymi z karty zwierzęcia,
F12 — publikacja wyłącznie po jawnej decyzji,
F14 — odznaka „dane potwierdzone w IRZplus” nadawana po porównaniu z rejestrem,
plus zapytania kupujących i udział ogłoszeń w synchronizacji przyrostowej.
"""
from fastapi.testclient import TestClient

from tests.conftest import SEED_ZWIERZE

ANIMAL = {
    "ear_tag": "PL004512378901",
    "name": "Krasula",
    "birth_date": "2021-03-14",
    "breed": "Holsztyńsko-fryzyjska",
    "sex": "female",
    "herd_id": "PL000111222",
}


def _animal(client: TestClient, **overrides) -> dict:
    response = client.post("/animals", json={**ANIMAL, **overrides})
    assert response.status_code == 201, response.text
    return response.json()


def _listing(client: TestClient, animal_id: int, price: float = 6500.0) -> dict:
    response = client.post(
        "/listings",
        json={"animal_id": animal_id, "price": price, "description": "Krowa mleczna."},
    )
    assert response.status_code == 201, response.text
    return response.json()


# --- F13: formularz wypełniony z karty zwierzęcia -----------------------

def test_szkic_ogloszenia_bierze_dane_z_karty_zwierzecia(client: TestClient):
    animal = _animal(client)

    draft = client.get(f"/animals/{animal['id']}/listing-draft")
    assert draft.status_code == 200, draft.text
    data = draft.json()

    assert data["ear_tag"] == ANIMAL["ear_tag"]
    assert data["breed"] == ANIMAL["breed"]
    assert data["birth_date"] == ANIMAL["birth_date"]
    assert data["sex"] == "female"
    assert ANIMAL["ear_tag"] in data["suggested_description"]
    assert data["existing_listing_id"] is None


def test_szkic_sygnalizuje_ze_zwierze_ma_juz_ogloszenie(client: TestClient):
    animal = _animal(client)
    listing = _listing(client, animal["id"])

    data = client.get(f"/animals/{animal['id']}/listing-draft").json()
    assert data["existing_listing_id"] == listing["id"]


def test_szkic_dla_nieistniejacego_zwierzecia_to_404(client: TestClient):
    assert client.get("/animals/999/listing-draft").status_code == 404


# --- F12: publikacja tylko po jawnej decyzji ----------------------------

def test_nowe_ogloszenie_jest_szkicem_a_nie_publikacja(client: TestClient):
    animal = _animal(client)
    listing = _listing(client, animal["id"])

    assert listing["status"] == "draft"
    assert listing["published_at"] is None


def test_dopiero_publish_czyni_ogloszenie_widocznym(client: TestClient):
    animal = _animal(client)
    listing = _listing(client, animal["id"])

    published = client.post(f"/listings/{listing['id']}/publish")
    assert published.status_code == 200, published.text
    assert published.json()["status"] == "published"
    assert published.json()["published_at"] is not None


def test_publikacja_jest_idempotentna(client: TestClient):
    animal = _animal(client)
    listing = _listing(client, animal["id"])
    first = client.post(f"/listings/{listing['id']}/publish").json()
    second = client.post(f"/listings/{listing['id']}/publish").json()

    assert second["status"] == "published"
    assert second["published_at"] == first["published_at"]


def test_jedno_zwierze_nie_moze_miec_dwoch_aktywnych_ogloszen(client: TestClient):
    animal = _animal(client)
    _listing(client, animal["id"])

    duplicate = client.post("/listings", json={"animal_id": animal["id"], "price": 7000.0})
    assert duplicate.status_code == 409


def test_wycofane_ogloszenie_zwalnia_zwierze(client: TestClient):
    animal = _animal(client)
    listing = _listing(client, animal["id"])
    assert client.post(f"/listings/{listing['id']}/withdraw").status_code == 200

    again = client.post("/listings", json={"animal_id": animal["id"], "price": 7000.0})
    assert again.status_code == 201


def test_sprzedanego_ogloszenia_nie_da_sie_edytowac(client: TestClient):
    animal = _animal(client)
    listing = _listing(client, animal["id"])
    client.post(f"/listings/{listing['id']}/publish")
    client.post(f"/listings/{listing['id']}/sold")

    response = client.put(f"/listings/{listing['id']}", json={"price": 100.0})
    assert response.status_code == 409


# --- F14: odznaka weryfikacji wobec rejestru ----------------------------

def test_zwierze_spoza_rejestru_nie_dostaje_odznaki(client: TestClient):
    animal = _animal(client)
    listing = _listing(client, animal["id"])

    result = client.post(f"/listings/{listing['id']}/verify")
    assert result.status_code == 200, result.text
    data = result.json()

    assert data["irz_verified"] is False
    assert "nie figuruje w rejestrze" in data["message"]


def test_zgodne_dane_daja_odznake(client: TestClient, irz_store):
    """Zwierzę z seedu mocka IRZplus — dane karty zgodne z rejestrem."""
    registry = irz_store.get_animal(SEED_ZWIERZE)
    animal = _animal(
        client,
        ear_tag=SEED_ZWIERZE,
        birth_date=registry.dataUrodzenia.isoformat(),
        breed=registry.rasa,
        herd_id=registry.numerSiedzibyStada,
        sex="female",
    )
    listing = _listing(client, animal["id"])

    data = client.post(f"/listings/{listing['id']}/verify").json()
    assert data["irz_verified"] is True, data["message"]
    assert data["irz_verified_at"] is not None
    assert data["discrepancies"] == []

    # Odznaka jest trwała — widzi ją każdy, kto pobierze ogłoszenie.
    assert client.get(f"/listings/{listing['id']}").json()["irz_verified"] is True


def test_rozbiezna_data_urodzenia_odbiera_odznake(client: TestClient, irz_store):
    registry = irz_store.get_animal(SEED_ZWIERZE)
    animal = _animal(
        client,
        ear_tag=SEED_ZWIERZE,
        birth_date="1999-01-01",
        breed=registry.rasa,
        herd_id=registry.numerSiedzibyStada,
    )
    listing = _listing(client, animal["id"])

    data = client.post(f"/listings/{listing['id']}/verify").json()
    assert data["irz_verified"] is False
    assert any("data urodzenia" in d for d in data["discrepancies"])


def test_zwierze_rozliczone_w_rejestrze_nie_moze_miec_odznaki(client: TestClient, irz_store):
    """Sztuka zgłoszona jako ubyła lub padła nie jest już przedmiotem sprzedaży."""
    registry = irz_store.get_animal(SEED_ZWIERZE)
    animal = _animal(
        client,
        ear_tag=SEED_ZWIERZE,
        birth_date=registry.dataUrodzenia.isoformat(),
        breed=registry.rasa,
        herd_id=registry.numerSiedzibyStada,
    )
    listing = _listing(client, animal["id"])
    irz_store.close_animal(SEED_ZWIERZE)

    data = client.post(f"/listings/{listing['id']}/verify").json()
    assert data["irz_verified"] is False
    assert "rozliczone" in data["message"]


def test_edycja_ogloszenia_uniewaznia_odznake(client: TestClient, irz_store):
    registry = irz_store.get_animal(SEED_ZWIERZE)
    animal = _animal(
        client,
        ear_tag=SEED_ZWIERZE,
        birth_date=registry.dataUrodzenia.isoformat(),
        breed=registry.rasa,
        herd_id=registry.numerSiedzibyStada,
    )
    listing = _listing(client, animal["id"])
    assert client.post(f"/listings/{listing['id']}/verify").json()["irz_verified"] is True

    edited = client.put(f"/listings/{listing['id']}", json={"price": 9000.0})
    assert edited.status_code == 200
    assert edited.json()["irz_verified"] is False


# --- Zapytania kupujących -----------------------------------------------

def test_kupujacy_wysyla_zapytanie_do_opublikowanego_ogloszenia(client: TestClient):
    animal = _animal(client)
    listing = _listing(client, animal["id"])
    client.post(f"/listings/{listing['id']}/publish")

    response = client.post(
        f"/listings/{listing['id']}/inquiries",
        json={"buyer": "Jan Kowalski", "contact": "601234567", "message": "Czy sztuka jest cielna?"},
    )
    assert response.status_code == 201, response.text
    assert response.json()["buyer"] == "Jan Kowalski"

    listed = client.get(f"/listings/{listing['id']}/inquiries").json()
    assert len(listed) == 1


def test_do_szkicu_nie_da_sie_wyslac_zapytania(client: TestClient):
    animal = _animal(client)
    listing = _listing(client, animal["id"])

    response = client.post(
        f"/listings/{listing['id']}/inquiries",
        json={"buyer": "Jan Kowalski", "message": "Pytanie"},
    )
    assert response.status_code == 409


def test_usuniecie_ogloszenia_kasuje_zapytania(client: TestClient):
    animal = _animal(client)
    listing = _listing(client, animal["id"])
    client.post(f"/listings/{listing['id']}/publish")
    client.post(
        f"/listings/{listing['id']}/inquiries",
        json={"buyer": "Jan Kowalski", "message": "Pytanie"},
    )

    assert client.delete(f"/listings/{listing['id']}").status_code == 204
    assert client.get(f"/listings/{listing['id']}/inquiries").status_code == 404


# --- Udział w synchronizacji przyrostowej -------------------------------

def test_ogloszenie_i_zapytanie_trafiaja_do_synchronizacji(client: TestClient):
    animal = _animal(client)
    checkpoint = client.get("/sync/changes").json()["server_time"]

    listing = _listing(client, animal["id"])
    client.post(f"/listings/{listing['id']}/publish")
    client.post(
        f"/listings/{listing['id']}/inquiries",
        json={"buyer": "Jan Kowalski", "message": "Czy dowieziecie?"},
    )

    changes = client.get("/sync/changes", params={"since": checkpoint}).json()
    assert len(changes["listings"]) == 1
    assert changes["listings"][0]["status"] == "published"
    assert len(changes["inquiries"]) == 1
    assert changes["inquiries"][0]["buyer"] == "Jan Kowalski"


def test_usuniete_ogloszenie_zostawia_nagrobki(client: TestClient):
    animal = _animal(client)
    listing = _listing(client, animal["id"])
    client.post(f"/listings/{listing['id']}/publish")
    inquiry = client.post(
        f"/listings/{listing['id']}/inquiries",
        json={"buyer": "Jan Kowalski", "message": "Pytanie"},
    ).json()
    checkpoint = client.get("/sync/changes").json()["server_time"]

    assert client.delete(f"/listings/{listing['id']}").status_code == 204

    removed = {
        (d["entity"], d["entity_id"])
        for d in client.get("/sync/changes", params={"since": checkpoint}).json()["deletions"]
    }
    assert ("listing", listing["id"]) in removed
    assert ("inquiry", inquiry["id"]) in removed


def test_usuniecie_zwierzecia_kasuje_takze_jego_ogloszenia(client: TestClient):
    animal = _animal(client)
    listing = _listing(client, animal["id"])
    checkpoint = client.get("/sync/changes").json()["server_time"]

    assert client.delete(f"/animals/{animal['id']}").status_code == 204

    removed = {
        (d["entity"], d["entity_id"])
        for d in client.get("/sync/changes", params={"since": checkpoint}).json()["deletions"]
    }
    assert ("animal", animal["id"]) in removed
    assert ("listing", listing["id"]) in removed
