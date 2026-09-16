"""Testy API ewidencji stada (zwierzęta, zdarzenia, leczenia) — cel synchronizacji z aplikacji."""
from fastapi.testclient import TestClient

EAR_TAG = "PL005506613419"


def utworz_zwierze(client: TestClient, ear_tag: str = EAR_TAG) -> dict:
    response = client.post("/animals", json={
        "ear_tag": ear_tag,
        "name": "Krasula",
        "birth_date": "2024-03-10",
        "breed": "HO",
        "sex": "female",
        "herd_id": "PL000111222",
    })
    assert response.status_code == 201
    return response.json()


def test_health(client: TestClient):
    assert client.get("/health").json() == {"status": "ok"}


class TestZwierzeta:
    def test_utworz_i_pobierz(self, client: TestClient):
        zwierze = utworz_zwierze(client)

        response = client.get(f"/animals/{zwierze['id']}")

        assert response.status_code == 200
        assert response.json()["ear_tag"] == EAR_TAG
        assert [z["id"] for z in client.get("/animals").json()] == [zwierze["id"]]

    def test_duplikat_kolczyka_zwraca_409(self, client: TestClient):
        utworz_zwierze(client)

        response = client.post("/animals", json={"ear_tag": EAR_TAG})

        assert response.status_code == 409

    def test_zly_format_kolczyka_zwraca_422(self, client: TestClient):
        assert client.post("/animals", json={"ear_tag": "PL12345"}).status_code == 422

    def test_nieznane_zwierze_zwraca_404(self, client: TestClient):
        assert client.get("/animals/999").status_code == 404
        assert client.delete("/animals/999").status_code == 404

    def test_usun(self, client: TestClient):
        zwierze = utworz_zwierze(client)

        assert client.delete(f"/animals/{zwierze['id']}").status_code == 204
        assert client.get(f"/animals/{zwierze['id']}").status_code == 404

    def test_paginacja(self, client: TestClient):
        for i in range(3):
            utworz_zwierze(client, ear_tag=f"PL00000000000{i}")

        assert len(client.get("/animals", params={"skip": 1, "limit": 1}).json()) == 1


class TestZdarzenia:
    def test_utworz_zdarzenie_ze_statusem_pending(self, client: TestClient):
        zwierze = utworz_zwierze(client)
        body = {"animal_id": zwierze["id"], "type": "birth", "event_date": "2024-03-10"}

        response = client.post(f"/animals/{zwierze['id']}/events", json=body)

        assert response.status_code == 201
        assert response.json()["irz_status"] == "pending"
        assert len(client.get(f"/animals/{zwierze['id']}/events").json()) == 1

    def test_niezgodne_animal_id_zwraca_400(self, client: TestClient):
        zwierze = utworz_zwierze(client)
        body = {"animal_id": zwierze["id"] + 1, "type": "birth", "event_date": "2024-03-10"}

        assert client.post(f"/animals/{zwierze['id']}/events", json=body).status_code == 400

    def test_zdarzenie_dla_nieznanego_zwierzecia_zwraca_404(self, client: TestClient):
        body = {"animal_id": 999, "type": "birth", "event_date": "2024-03-10"}

        assert client.post("/animals/999/events", json=body).status_code == 404
        assert client.get("/animals/999/events").status_code == 404

    def test_nieznany_typ_zwraca_422(self, client: TestClient):
        zwierze = utworz_zwierze(client)
        body = {"animal_id": zwierze["id"], "type": "wedding", "event_date": "2024-03-10"}

        assert client.post(f"/animals/{zwierze['id']}/events", json=body).status_code == 422

    def test_usun_zdarzenie(self, client: TestClient):
        zwierze = utworz_zwierze(client)
        body = {"animal_id": zwierze["id"], "type": "death", "event_date": "2024-05-01"}
        zdarzenie = client.post(f"/animals/{zwierze['id']}/events", json=body).json()

        assert client.delete(f"/events/{zdarzenie['id']}").status_code == 204
        assert client.delete(f"/events/{zdarzenie['id']}").status_code == 404


class TestLeczenia:
    def test_utworz_i_usun_leczenie(self, client: TestClient):
        zwierze = utworz_zwierze(client)
        body = {
            "animal_id": zwierze["id"],
            "drug": "Penicylina",
            "dose": "10 ml",
            "treatment_date": "2024-04-01",
            "withdrawal_until": "2024-04-15",
        }

        created = client.post(f"/animals/{zwierze['id']}/treatments", json=body)

        assert created.status_code == 201
        assert created.json()["withdrawal_until"] == "2024-04-15"
        assert client.delete(f"/treatments/{created.json()['id']}").status_code == 204
        assert client.get(f"/animals/{zwierze['id']}/treatments").json() == []

    def test_pusta_nazwa_leku_zwraca_422(self, client: TestClient):
        zwierze = utworz_zwierze(client)
        body = {"animal_id": zwierze["id"], "drug": "", "treatment_date": "2024-04-01"}

        assert client.post(f"/animals/{zwierze['id']}/treatments", json=body).status_code == 422
