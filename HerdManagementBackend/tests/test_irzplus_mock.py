"""Testy integracyjne symulatora API IRZplus.

Weryfikują pełny przepływ HTTP: logowanie, uzyskanie tokenu Bearer, pobieranie danych
oraz zgłoszenia (urodzenie, przybycie, ubycie, padnięcie) wraz z walidacją.
"""
from datetime import date, timedelta
from itertools import count

import pytest
from fastapi.testclient import TestClient

from app.irzplus.service import TOKEN_TTL_SECONDS, cyfra_kontrolna, poprawny_numer_zwierzecia
from tests.conftest import PRODUCENT, SEED_ZWIERZE, SIEDZIBA

BASE = "/irzplus"
URODZENIE = f"{BASE}/dokument/api/prod/zgloszenieUrodzenia"
PRZYBYCIE = f"{BASE}/dokument/api/prod/zgloszeniePrzybycia"
UBYCIE = f"{BASE}/dokument/api/prod/zgloszenieUbycia"
PADNIECIE = f"{BASE}/dokument/api/prod/zgloszeniePadniecia"
ZWIERZETA = f"{BASE}/zwierze/api/prod/zwierzetaProducenta"
ZWIERZE = f"{BASE}/zwierze/api/prod/zwierzeIndywidualne"
PARTNER = "PL000333444"
MATKA = SEED_ZWIERZE

_numery = count(1)


def nowy_numer() -> str:
    """Zwraca unikalny, poprawny numer identyfikacyjny bydła."""
    cyfry = f"{next(_numery):011d}"
    return f"PL{cyfry}{cyfra_kontrolna(cyfry)}"


def z_bledna_cyfra(numer: str) -> str:
    return numer[:-1] + str((int(numer[-1]) + 1) % 10)


def dyspozycja(*pozycje: dict) -> dict:
    return {"numerProducenta": PRODUCENT, "zgloszenie": {"pozycje": list(pozycje)}}


def pozycja_urodzenia(numer: str, **zmiany) -> dict:
    dzis = date.today()
    pozycja = {
        "numerIdentyfikacyjnyZwierzecia": numer,
        "numerSiedzibyStada": SIEDZIBA,
        "dataZdarzenia": dzis.isoformat(),
        "dataUrodzenia": dzis.isoformat(),
        "plec": "samica",
        "rasa": "HO",
        "numerIdentyfikacyjnyMatki": MATKA,
    }
    pozycja.update(zmiany)
    return pozycja


def pozycja_przemieszczenia(numer: str, **zmiany) -> dict:
    pozycja = {
        "numerIdentyfikacyjnyZwierzecia": numer,
        "numerSiedzibyStada": SIEDZIBA,
        "dataZdarzenia": date.today().isoformat(),
        "numerSiedzibyStadaPartnera": PARTNER,
    }
    pozycja.update(zmiany)
    return pozycja


def kody_bledow(response) -> set[str]:
    return {b["kod"] for b in response.json()["bledy"]}


def assert_przyjete(response, prefix: str) -> str:
    assert response.status_code == 200
    body = response.json()
    assert body["bledy"] == []
    assert body["numerDokumentu"].startswith(f"{prefix}/{date.today().year}/")
    assert body["numerDokumentu"] in body["komunikat"]
    return body["numerDokumentu"]


def assert_odrzucone(response, *kody: str) -> None:
    assert response.status_code == 200
    body = response.json()
    assert body["numerDokumentu"] is None
    assert set(kody) <= kody_bledow(response)



# Numery buhajów z publicznych katalogów (MCHiRZ, PZHiPBM).
PRAWDZIWE_NUMERY = [
    "PL005506613419", "PL005544460631", "PL005458914206", "PL005539084644",
    "PL005546525222", "PL005499385546", "PL005000168286", "PL005308805029",
]


@pytest.mark.parametrize("numer", PRAWDZIWE_NUMERY)
def test_cyfra_kontrolna_zgodna_z_rzeczywistymi_numerami(numer: str):
    assert poprawny_numer_zwierzecia(numer)


@pytest.mark.parametrize("numer", [z_bledna_cyfra(n) for n in PRAWDZIWE_NUMERY] + ["PL12", "PL00550661341X"])
def test_bledne_numery_odrzucone(numer: str):
    assert not poprawny_numer_zwierzecia(numer)


def test_seed_ma_poprawny_numer():
    assert poprawny_numer_zwierzecia(SEED_ZWIERZE)



class TestAutoryzacja:
    def test_logowanie_zwraca_token_bearer(self, client: TestClient):
        response = client.post(f"{BASE}/logowanie", json={"login": PRODUCENT, "haslo": "demo"})

        assert response.status_code == 200
        body = response.json()
        assert body["token"]
        assert body["typTokenu"] == "Bearer"
        assert body["wygasaZaSekundy"] == TOKEN_TTL_SECONDS

    def test_kazde_logowanie_daje_inny_token(self, client: TestClient):
        dane = {"login": PRODUCENT, "haslo": "demo"}
        t1 = client.post(f"{BASE}/logowanie", json=dane).json()["token"]
        t2 = client.post(f"{BASE}/logowanie", json=dane).json()["token"]

        assert t1 != t2

    @pytest.mark.parametrize("dane", [
        {"login": "", "haslo": "demo"},
        {"login": PRODUCENT, "haslo": ""},
    ])
    def test_logowanie_z_pustymi_danymi_zwraca_401(self, client: TestClient, dane: dict):
        assert client.post(f"{BASE}/logowanie", json=dane).status_code == 401

    def test_brak_naglowka_zwraca_401(self, client: TestClient):
        response = client.get(ZWIERZETA)

        assert response.status_code == 401
        assert response.headers["www-authenticate"] == "Bearer"

    def test_nieznany_token_zwraca_401(self, client: TestClient):
        response = client.get(ZWIERZETA, headers={"Authorization": "Bearer zly-token"})

        assert response.status_code == 401

    def test_zly_schemat_autoryzacji_zwraca_401(self, client: TestClient):
        response = client.get(ZWIERZETA, headers={"Authorization": "Basic abc"})

        assert response.status_code == 401

    def test_wygasly_token_zwraca_401(self, client: TestClient, irz_store, monkeypatch):
        token, _ = irz_store.issue_token(PRODUCENT)
        producent, expiry = irz_store._tokens[token]
        irz_store._tokens[token] = (producent, expiry - timedelta(seconds=TOKEN_TTL_SECONDS + 1))

        response = client.get(ZWIERZETA, headers={"Authorization": f"Bearer {token}"})

        assert response.status_code == 401
        assert token not in irz_store._tokens

    @pytest.mark.parametrize("sciezka", [URODZENIE, PRZYBYCIE, UBYCIE, PADNIECIE])
    def test_zgloszenia_wymagaja_tokenu(self, client: TestClient, sciezka: str):
        response = client.post(sciezka, json=dyspozycja(pozycja_urodzenia(nowy_numer())))

        assert response.status_code == 401



class TestPobieranieDanych:
    def test_lista_zawiera_zwierze_z_seeda(self, client: TestClient, auth_headers):
        response = client.get(ZWIERZETA, headers=auth_headers)

        assert response.status_code == 200
        numery = [z["numerIdentyfikacyjnyZwierzecia"] for z in response.json()]
        assert numery == [SEED_ZWIERZE]

    def test_dane_pojedynczego_zwierzecia(self, client: TestClient, auth_headers):
        response = client.get(
            ZWIERZE,
            params={"numerIdentyfikacyjnyZwierzecia": SEED_ZWIERZE},
            headers=auth_headers,
        )

        assert response.status_code == 200
        body = response.json()
        assert body["numerSiedzibyStada"] == SIEDZIBA
        assert body["plec"] == "samica"
        assert body["dataUrodzenia"] == "2023-04-01"

    def test_nieznane_zwierze_zwraca_404(self, client: TestClient, auth_headers):
        response = client.get(
            ZWIERZE,
            params={"numerIdentyfikacyjnyZwierzecia": nowy_numer()},
            headers=auth_headers,
        )

        assert response.status_code == 404

    def test_brak_parametru_numeru_zwraca_422(self, client: TestClient, auth_headers):
        assert client.get(ZWIERZE, headers=auth_headers).status_code == 422



class TestWalidacjaWspolna:
    @pytest.mark.parametrize("sciezka", [URODZENIE, PRZYBYCIE, UBYCIE, PADNIECIE])
    def test_pusta_lista_pozycji(self, client: TestClient, auth_headers, sciezka: str):
        response = client.post(sciezka, json=dyspozycja(), headers=auth_headers)

        assert_odrzucone(response, "ERR_BRAK_POZYCJI")

    @pytest.mark.parametrize("zly_numer", ["PL123", "DE005338887000", "PL00533888700X", "005338887000"])
    def test_zly_format_numeru_zwierzecia(self, client: TestClient, auth_headers, zly_numer: str):
        response = client.post(URODZENIE, json=dyspozycja(pozycja_urodzenia(zly_numer)), headers=auth_headers)

        assert_odrzucone(response, "ERR_NUMER_ZWIERZECIA")

    @pytest.mark.parametrize("sciezka", [URODZENIE, PRZYBYCIE, UBYCIE, PADNIECIE])
    def test_bledna_cyfra_kontrolna(self, client: TestClient, auth_headers, sciezka: str):
        pozycja = pozycja_urodzenia(z_bledna_cyfra(nowy_numer()), numerSiedzibyStadaPartnera=PARTNER)

        response = client.post(sciezka, json=dyspozycja(pozycja), headers=auth_headers)

        assert_odrzucone(response, "ERR_CYFRA_KONTROLNA")
        assert "ERR_NUMER_ZWIERZECIA" not in kody_bledow(response)

    def test_brak_siedziby_stada(self, client: TestClient, auth_headers):
        pozycja = pozycja_przemieszczenia(nowy_numer(), numerSiedzibyStada="")

        response = client.post(PRZYBYCIE, json=dyspozycja(pozycja), headers=auth_headers)

        assert_odrzucone(response, "ERR_SIEDZIBA_STADA")

    def test_data_z_przyszlosci(self, client: TestClient, auth_headers):
        jutro = (date.today() + timedelta(days=1)).isoformat()
        pozycja = pozycja_przemieszczenia(nowy_numer(), dataZdarzenia=jutro)

        response = client.post(PRZYBYCIE, json=dyspozycja(pozycja), headers=auth_headers)

        assert_odrzucone(response, "ERR_DATA_PRZYSZLA")

    def test_bledy_wskazuja_numer_zwierzecia(self, client: TestClient, auth_headers):
        numer = nowy_numer()
        pozycja = pozycja_przemieszczenia(numer, numerSiedzibyStadaPartnera=None)

        response = client.post(PRZYBYCIE, json=dyspozycja(pozycja), headers=auth_headers)

        assert all(b["numerZwierzecia"] == numer for b in response.json()["bledy"])

    def test_brak_wymaganego_pola_zwraca_422(self, client: TestClient, auth_headers):
        pozycja = pozycja_przemieszczenia(nowy_numer())
        del pozycja["dataZdarzenia"]

        response = client.post(PRZYBYCIE, json=dyspozycja(pozycja), headers=auth_headers)

        assert response.status_code == 422



class TestUrodzenie:
    def test_przyjete_rejestruje_zwierze(self, client: TestClient, auth_headers):
        numer = nowy_numer()

        response = client.post(URODZENIE, json=dyspozycja(pozycja_urodzenia(numer)), headers=auth_headers)

        assert_przyjete(response, "URO")
        zwierze = client.get(ZWIERZE, params={"numerIdentyfikacyjnyZwierzecia": numer}, headers=auth_headers)
        assert zwierze.status_code == 200
        assert zwierze.json()["numerIdentyfikacyjnyMatki"] == MATKA

    def test_numery_dokumentow_sa_kolejne(self, client: TestClient, auth_headers):
        rok = date.today().year
        n1 = assert_przyjete(client.post(URODZENIE, json=dyspozycja(pozycja_urodzenia(nowy_numer())),
                                         headers=auth_headers), "URO")
        n2 = assert_przyjete(client.post(URODZENIE, json=dyspozycja(pozycja_urodzenia(nowy_numer())),
                                         headers=auth_headers), "URO")

        assert (n1, n2) == (f"URO/{rok}/000001", f"URO/{rok}/000002")

    def test_duplikat_zwierzecia(self, client: TestClient, auth_headers):
        response = client.post(URODZENIE, json=dyspozycja(pozycja_urodzenia(SEED_ZWIERZE)), headers=auth_headers)

        assert_odrzucone(response, "ERR_ZWIERZE_ISTNIEJE")

    def test_brak_daty_urodzenia(self, client: TestClient, auth_headers):
        pozycja = pozycja_urodzenia(nowy_numer(), dataUrodzenia=None)

        response = client.post(URODZENIE, json=dyspozycja(pozycja), headers=auth_headers)

        assert_odrzucone(response, "ERR_BRAK_DATY_URODZENIA")

    @pytest.mark.parametrize("plec", [None, "", "krowa"])
    def test_niepoprawna_plec(self, client: TestClient, auth_headers, plec):
        pozycja = pozycja_urodzenia(nowy_numer(), plec=plec)

        response = client.post(URODZENIE, json=dyspozycja(pozycja), headers=auth_headers)

        assert_odrzucone(response, "ERR_PLEC")

    def test_brak_numeru_matki(self, client: TestClient, auth_headers):
        pozycja = pozycja_urodzenia(nowy_numer(), numerIdentyfikacyjnyMatki=None)

        response = client.post(URODZENIE, json=dyspozycja(pozycja), headers=auth_headers)

        assert_odrzucone(response, "ERR_BRAK_MATKI")

    @pytest.mark.parametrize("matka", ["PL12", z_bledna_cyfra(SEED_ZWIERZE)])
    def test_zly_numer_matki(self, client: TestClient, auth_headers, matka: str):
        pozycja = pozycja_urodzenia(nowy_numer(), numerIdentyfikacyjnyMatki=matka)

        response = client.post(URODZENIE, json=dyspozycja(pozycja), headers=auth_headers)

        assert_odrzucone(response, "ERR_NUMER_MATKI")

    def test_data_zdarzenia_przed_urodzeniem(self, client: TestClient, auth_headers):
        dzis = date.today()
        pozycja = pozycja_urodzenia(
            nowy_numer(),
            dataUrodzenia=dzis.isoformat(),
            dataZdarzenia=(dzis - timedelta(days=3)).isoformat(),
        )

        response = client.post(URODZENIE, json=dyspozycja(pozycja), headers=auth_headers)

        assert_odrzucone(response, "ERR_DATA_PRZED_URODZENIEM")

    def test_wiele_bledow_naraz(self, client: TestClient, auth_headers):
        pozycja = pozycja_urodzenia("PL1", plec=None, numerIdentyfikacyjnyMatki=None, dataUrodzenia=None)

        response = client.post(URODZENIE, json=dyspozycja(pozycja), headers=auth_headers)

        assert kody_bledow(response) == {
            "ERR_NUMER_ZWIERZECIA", "ERR_PLEC", "ERR_BRAK_MATKI", "ERR_BRAK_DATY_URODZENIA",
        }

    def test_blad_jednej_pozycji_odrzuca_cale_zgloszenie(self, client: TestClient, auth_headers):
        dobry = nowy_numer()
        body = dyspozycja(pozycja_urodzenia(dobry), pozycja_urodzenia("PL1"))

        response = client.post(URODZENIE, json=body, headers=auth_headers)

        assert_odrzucone(response, "ERR_NUMER_ZWIERZECIA")
        zwierze = client.get(ZWIERZE, params={"numerIdentyfikacyjnyZwierzecia": dobry}, headers=auth_headers)
        assert zwierze.status_code == 404



class TestPrzemieszczenia:
    def test_przybycie_rejestruje_zwierze(self, client: TestClient, auth_headers):
        numer = nowy_numer()

        response = client.post(PRZYBYCIE, json=dyspozycja(pozycja_przemieszczenia(numer)), headers=auth_headers)

        assert_przyjete(response, "PRZYB")
        lista = client.get(ZWIERZETA, headers=auth_headers).json()
        assert numer in {z["numerIdentyfikacyjnyZwierzecia"] for z in lista}

    def test_przybycie_wymaga_siedziby_partnera(self, client: TestClient, auth_headers):
        pozycja = pozycja_przemieszczenia(nowy_numer(), numerSiedzibyStadaPartnera=None)

        response = client.post(PRZYBYCIE, json=dyspozycja(pozycja), headers=auth_headers)

        assert_odrzucone(response, "ERR_BRAK_SIEDZIBY_PARTNERA")

    def test_ubycie_znanego_zwierzecia(self, client: TestClient, auth_headers):
        response = client.post(UBYCIE, json=dyspozycja(pozycja_przemieszczenia(SEED_ZWIERZE)),
                               headers=auth_headers)

        assert_przyjete(response, "UBYC")

    def test_ubycie_wymaga_siedziby_partnera(self, client: TestClient, auth_headers):
        pozycja = pozycja_przemieszczenia(SEED_ZWIERZE, numerSiedzibyStadaPartnera="")

        response = client.post(UBYCIE, json=dyspozycja(pozycja), headers=auth_headers)

        assert_odrzucone(response, "ERR_BRAK_SIEDZIBY_PARTNERA")

    def test_ubycie_nieznanego_zwierzecia(self, client: TestClient, auth_headers):
        response = client.post(UBYCIE, json=dyspozycja(pozycja_przemieszczenia(nowy_numer())),
                               headers=auth_headers)

        assert_odrzucone(response, "ERR_ZWIERZE_NIEZNANE")

    def test_padniecie_znanego_zwierzecia(self, client: TestClient, auth_headers):
        pozycja = pozycja_przemieszczenia(SEED_ZWIERZE, numerSiedzibyStadaPartnera=None,
                                          przyczynaPadniecia="choroba")

        response = client.post(PADNIECIE, json=dyspozycja(pozycja), headers=auth_headers)

        assert_przyjete(response, "PAD")

    def test_padniecie_nieznanego_zwierzecia(self, client: TestClient, auth_headers):
        response = client.post(PADNIECIE, json=dyspozycja(pozycja_przemieszczenia(nowy_numer())),
                               headers=auth_headers)

        assert_odrzucone(response, "ERR_ZWIERZE_NIEZNANE")

    @pytest.mark.parametrize("pierwsze, drugie", [
        (UBYCIE, UBYCIE),
        (UBYCIE, PADNIECIE),
        (PADNIECIE, UBYCIE),
        (PADNIECIE, PADNIECIE),
    ])
    def test_rozliczonego_zwierzecia_nie_mozna_zglosic_ponownie(
        self, client: TestClient, auth_headers, pierwsze: str, drugie: str,
    ):
        body = dyspozycja(pozycja_przemieszczenia(SEED_ZWIERZE))
        client.post(pierwsze, json=body, headers=auth_headers)

        response = client.post(drugie, json=body, headers=auth_headers)

        assert_odrzucone(response, "ERR_ZWIERZE_ROZLICZONE")

    def test_ponowne_przybycie_przywraca_rozliczone_zwierze(self, client: TestClient, auth_headers):
        body = dyspozycja(pozycja_przemieszczenia(SEED_ZWIERZE))
        assert_przyjete(client.post(UBYCIE, json=body, headers=auth_headers), "UBYC")
        assert_przyjete(client.post(PRZYBYCIE, json=body, headers=auth_headers), "PRZYB")

        response = client.post(UBYCIE, json=body, headers=auth_headers)

        assert_przyjete(response, "UBYC")



def test_cykl_zycia_zwierzecia(client: TestClient, auth_headers):
    """Pełny cykl życia: ubycie jest możliwe dopiero po zarejestrowaniu urodzenia."""
    numer = nowy_numer()
    ubycie = dyspozycja(pozycja_przemieszczenia(numer))

    assert_odrzucone(client.post(UBYCIE, json=ubycie, headers=auth_headers), "ERR_ZWIERZE_NIEZNANE")
    assert_przyjete(client.post(URODZENIE, json=dyspozycja(pozycja_urodzenia(numer)), headers=auth_headers), "URO")
    assert_przyjete(client.post(UBYCIE, json=ubycie, headers=auth_headers), "UBYC")
    assert_odrzucone(
        client.post(PADNIECIE, json=dyspozycja(pozycja_przemieszczenia(numer)), headers=auth_headers),
        "ERR_ZWIERZE_ROZLICZONE",
    )
