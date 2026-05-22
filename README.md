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
- **LLM:** orkiestracja wywołań narzędzi przez backend (klucz API chowany po stronie serwera)

## Uruchomienie

### Backend

```powershell
cd HerdManagementBackend
.venv\Scripts\Activate.ps1
pip install -r requirements.txt    # po utworzeniu pliku
uvicorn main:app --reload
```

### Aplikacja mobilna

Otworzyć `HerdManagementMobile/` w Android Studio (Hedgehog lub nowszym), zsynchronizować Gradle, uruchomić na emulatorze (API 28+) lub urządzeniu fizycznym.

## Stan prac

Aktualny stan względem karty pracy:

| # | Zadanie | Status |
|---|---------|--------|
| 1 | Analiza prawna + dokumentacja IRZplus + user stories | do zrobienia |
| 2 | Architektura systemu + model danych | w trakcie (dokument `docs/plan.tex`) |
| 3 | Lokalna baza SQLite + ekrany ewidencji | do zrobienia |
| 4 | Skaner kodów kreskowych/QR + OCR numeru kolczyka | do zrobienia |
| 5 | Odczyt NFC/RFID (ISO 11784/11785) | do zrobienia |
| 6 | Klient API IRZplus | do zrobienia |
| 7 | Kolejka zgłoszeń offline + widok zgodności | do zrobienia |
| 8 | Asystent LLM z narzędziami | do zrobienia |
| 9 | Moduł publikacji ogłoszeń | do zrobienia |
| 10 | Omówienie wyników i wnioski | do zrobienia |

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
