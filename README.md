# Inteligentny asystent hodowcy bydła

Mobilna aplikacja do zarządzania stadem bydła z integracją z państwowym systemem IRZplus (ARiMR), skanowaniem kolczyków urzędowych, asystentem opartym o duży model językowy (LLM) oraz modułem publikacji ogłoszeń sprzedażowych. Projekt realizowany jako praca inżynierska.

## Cel projektu

Zaprojektowanie i implementacja mobilnej aplikacji redukującej obciążenia administracyjne polskiego hodowcy bydła poprzez:

- skanowanie kolczyków urzędowych (kody kreskowe/QR + OCR numeru 14-znakowego + RFID/NFC zgodne z ISO 11784/11785),
- automatyzację zgłoszeń zdarzeń zwierzęcych (urodzenie, przybycie, ubycie, padnięcie) do systemu IRZplus,
- asystenta tekstowego (LLM) z dostępem do narzędzi backendowych i polityką potwierdzania akcji,
- publikację ogłoszeń sprzedażowych z weryfikacją „dane potwierdzone w IRZplus".

## Struktura repozytorium

```
PracaInzynierska/
├── HerdManagementMobile/   # Aplikacja Android (Kotlin + Jetpack Compose)
├── HerdManagementBackend/  # Backend (Python / FastAPI)
├── docs/                   # Dokumentacja techniczna (LaTeX)
│   └── plan.tex            # Plan/dokumentacja projektu
└── README.md
```

## Stos technologiczny

### Aplikacja mobilna (`HerdManagementMobile/`)
- **Język / UI:** Kotlin 2.0, Jetpack Compose, Material 3
- **Baza lokalna:** SQLite (poprzez Room) — planowane
- **Skanowanie:** CameraX + ML Kit (kody kreskowe/QR, OCR) — planowane
- **NFC:** Android NFC API — planowane
- **Sieć:** Retrofit + OkHttp — planowane
- **min SDK:** 28 (Android 9), **target SDK:** 36

### Backend (`HerdManagementBackend/`)
- **Framework:** FastAPI (Python 3.12)
- **Baza danych:** PostgreSQL (planowane, na czas demo SQLite)
- **Integracja:** klient HTTP do API IRZplus (ARiMR)
- **LLM:** orkiestracja wywołań narzędzi przez backend — SDK `anthropic`, model `claude-opus-5` (klucz API chowany po stronie serwera)

## Uruchomienie

### Backend

```powershell
cd HerdManagementBackend
.venv\Scripts\Activate.ps1
pip install -r requirements.txt    # po utworzeniu pliku
uvicorn main:app --reload
```

Asystent LLM wymaga klucza do modelu — bez niego reszta API działa normalnie,
a `GET /assistant/status` zwraca `configured: false`:

```powershell
cd HerdManagementBackend
copy .env.example .env      # i wpisać własny klucz w LLM_API_KEY
```

Testy (pytest — m.in. testy integracyjne mocka IRZplus i pętli agentowej na atrapie modelu):

```powershell
cd HerdManagementBackend
python -m pytest
```

Asystenta można sprawdzić ręcznie z aplikacji mobilnej albo przez Swagger UI
(`http://127.0.0.1:8000/docs`, sekcja `assistant`).

### Aplikacja mobilna

Otworzyć `HerdManagementMobile/` w Android Studio (Hedgehog lub nowszym), zsynchronizować Gradle, uruchomić na emulatorze (API 28+) lub urządzeniu fizycznym.

## Stan prac

Aktualny stan względem karty pracy:

| # | Zadanie | Status |
|---|---------|--------|
| 1 | Analiza prawna + dokumentacja IRZplus + user stories | w trakcie |
| 2 | Architektura systemu + model danych | zrobione (`docs/plan.tex`) |
| 3 | Lokalna baza SQLite + ekrany ewidencji + statystyki | zrobione |
| 4 | Skaner kodów kreskowych/QR + walidacja cyfry kontrolnej | zrobione |
| 5 | Odczyt NFC/RFID (ISO 11784/11785) | zrobione |
| 6 | Klient API IRZplus + mock Portalu | zrobione |
| 7 | Kolejka zgłoszeń offline + widok zgodności 7-dniowej | zrobione |
| 8 | Asystent LLM z narzędziami i polityką potwierdzania akcji | zrobione |
| 9 | Moduł publikacji ogłoszeń | zrobione |
| 10 | Omówienie wyników i wnioski | do zrobienia |

## Asystent LLM (zadanie 8)

Asystent działa po stronie backendu (`app/assistant/`) — telefon wysyła tylko tekst
i identyfikator rozmowy, klucz API modelu nigdy nie trafia do aplikacji.

Narzędzia udostępnione modelowi:

| Narzędzie | Rodzaj | Opis |
|---|---|---|
| `lista_zwierzat` | odczyt | lista/wyszukiwanie sztuk w ewidencji |
| `dane_zwierzecia` | odczyt | karta sztuki: dane, zdarzenia, leczenia |
| `statystyki_stada` | odczyt | liczebność, płeć, grupy wiekowe, rasy |
| `zdarzenia_do_zgloszenia` | odczyt | zdarzenia niepotwierdzone przez IRZ + termin 7 dni |
| `aktywne_karencje` | odczyt | trwające okresy karencji |
| `dodaj_zwierze` | zapis | nowa sztuka w ewidencji |
| `dodaj_zdarzenie` | zapis | urodzenie / przybycie / ubycie / padnięcie |
| `dodaj_leczenie` | zapis | zabieg wraz z karencją |

Polityka potwierdzania akcji: narzędzia odczytowe wykonują się od razu, a każde
narzędzie zapisujące zatrzymuje pętlę agentową — backend zwraca `pending_action`,
aplikacja pokazuje pytanie („Zatwierdź” / „Odrzuć”), i dopiero `POST /assistant/confirm`
wykonuje zapis. Wymusza to backend, nie treść promptu. Wszystkie akcje (również
odrzucone) trafiają do dziennika `GET /assistant/audit`.

Endpointy: `GET /assistant/status`, `POST /assistant/chat`, `POST /assistant/confirm`,
`GET /assistant/conversations/{id}`, `DELETE /assistant/conversations/{id}`, `GET /assistant/audit`.

## Moduł publikacji ogłoszeń (zadanie 9)

Giełda sprzedażowa korzysta z tej samej karty zwierzęcia co ewidencja rejestrowa —
ogłoszenie wskazuje sztukę, a nie kopiuje jej danych. Trzy wymagania sterują
kształtem modułu:

- **F13 — formularz wypełniony z karty.** `GET /animals/{id}/listing-draft` zwraca
  kolczyk, rasę, datę urodzenia i szkic opisu. Hodowca uzupełnia tylko cenę.
- **F12 — publikacja po jawnej decyzji.** Ogłoszenie powstaje jako `draft`
  i dopiero `POST /listings/{id}/publish` czyni je widocznym; aplikacja pyta
  o potwierdzenie w dialogu. Wymusza to model danych, nie sam interfejs.
- **F14 — odznaka „dane potwierdzone w IRZplus".** `POST /listings/{id}/verify`
  porównuje kartę zwierzęcia z rejestrem: sprawdza, czy kolczyk w nim figuruje,
  czy sztuka nie jest rozliczona (ubycie/padnięcie) oraz czy data urodzenia, płeć,
  rasa, siedziba stada i numer matki się zgadzają. Rozbieżność odbiera odznakę
  i pokazuje konkretny powód. Edycja treści ogłoszenia także ją unieważnia.

Oznaczenie sprzedaży dotyczy tylko giełdy — ubycie zwierzęcia to osobne zdarzenie
z własnym obowiązkiem zgłoszenia do IRZplus w ciągu 7 dni, o czym aplikacja
przypomina.

### Zakres: strona kupującego

`HerdManagementMobile` jest aplikacją **hodowcy**. Kupujący nie jest jej użytkownikiem,
więc aplikacja kliencka giełdy (przeglądanie ogłoszeń, wysyłanie zapytań) pozostaje
**poza zakresem pracy** — backend wystawia dla niej gotowe API, ale nie ma dla niego
interfejsu. W praktyce oznacza to, że:

- `POST /listings/{id}/inquiries` wywołuje się przez Swagger UI (`/docs`) albo `curl`,
- w aplikacji hodowcy zapytania są wyłącznie **odbierane**: przychodzą synchronizacją
  pobierającą i pokazują się na karcie ogłoszenia oraz jako licznik na liście.

### Warunek nadania odznaki

Weryfikacja porównuje dane z **rejestrem**, a nie z ewidencją hodowcy. W mocku IRZplus
zwierzę pojawia się w rejestrze dopiero po zgłoszeniu urodzenia albo przybycia, więc
sztuka nigdy niezgłoszona dostanie komunikat „kolczyk nie figuruje w rejestrze IRZplus".
Pełna ścieżka demonstracyjna: karta zwierzęcia → zdarzenie urodzenia/przybycia →
„Zgłoś do IRZplus" → karta ogłoszenia → „Sprawdź w IRZplus" → odznaka.

Endpointy: `GET /listings`, `GET /listings/{id}`, `POST /listings`, `PUT /listings/{id}`,
`POST /listings/{id}/{publish|withdraw|sold|verify}`, `DELETE /listings/{id}`,
`GET|POST /listings/{id}/inquiries`, `GET /animals/{id}/listing-draft`.

## Synchronizacja dwukierunkowa (wymaganie N5)

Aplikacja działa offline na lokalnej bazie Room, ale ewidencja zmienia się też poza
telefonem — zapisuje w niej asystent LLM, można ją poprawić bezpośrednio w backendzie
albo z drugiego urządzenia. Hodowca podejmuje decyzje na podstawie tego, co widzi na
ekranie, więc rozjazd telefonu z serwerem jest błędem, nie niedogodnością.

- **Wysyłka** — `SyncManager` kolejkuje każdą zmianę lokalną, `SyncWorker` drenuje
  kolejkę FIFO (z mapowaniem identyfikatorów lokalnych na zdalne).
- **Pobieranie** — `GET /sync/changes?since=...` zwraca wyłącznie rekordy zmienione
  po podanym znaczniku oraz „nagrobki” po usuniętych (tabela `deletions`; bez nich
  telefon trzymałby skasowany rekord w nieskończoność). `SyncPuller` wgrywa je do Room.
- **Znacznik** — aplikacja przechowuje `server_time` z odpowiedzi i oddaje go nietknięty
  w kolejnym zapytaniu. Liczy się zegar serwera, nie telefonu.
- **Konflikty** — pobieranie rusza dopiero po opróżnieniu kolejki wysyłkowej. Dopóki
  cokolwiek czeka na wysłanie, wygrywa wersja lokalna; potem wersja z serwera nadpisuje
  lokalną. Zmiana użytkownika nigdy nie ginie, a stan i tak się zbiega.
- **Wyzwalacze** — start aplikacji, każda zmiana lokalna, zatwierdzenie akcji asystenta,
  kliknięcie chipa synchronizacji oraz zadanie cykliczne co 15 minut.

Synchronizacji podlegają zwierzęta, zdarzenia, leczenia, ogłoszenia i zapytania
kupujących.

Testy: `tests/test_sync.py` i `tests/test_listings.py` (backend) oraz `SyncPullerTest`
(mapowanie identyfikatorów, polityka konfliktów, nagrobki, ogłoszenia i zapytania).

## Dokumentacja

Szczegółowy plan projektu, architektura i harmonogram znajdują się w pliku [`docs/plan.tex`](docs/plan.tex).

Kompilacja:

```powershell
cd docs
pdflatex plan.tex
pdflatex plan.tex    # drugie przejście dla spisu treści
```

## Autor

- **Autor:** sulikk
- **Kierunek:** Informatyka (praca inżynierska)
- **Rok akademicki:** 2025/2026
