"""Mock API Portalu IRZplus (ARiMR) — moduł symulujący system państwowy.

Odwzorowuje konwencje realnego API: logowanie -> token Bearer, zgłoszenia jako
dyspozycje z pozycjami, odpowiedź z numerem dokumentu i listą błędów walidacji.
Operacje wymagane przez kartę pracy: pobierz listę zwierząt + zgłoś
urodzenie / przybycie / ubycie / padnięcie.
"""
from fastapi import APIRouter, Depends, Header, HTTPException, status

from collections.abc import Callable

from app.irzplus.schemas import (
    BladWalidacjiDTO,
    DyspozycjaZgloszenia,
    LoginRequest,
    LoginResponse,
    PozycjaZgloszeniaDTO,
    ZlozenieDyspozycjiResponse,
    ZwierzeIndywidualneDTO,
)
from app.irzplus.service import (
    IrzplusStore,
    store,
    validate_padniecie,
    validate_przybycie,
    validate_ubycie,
    validate_urodzenie,
)

router = APIRouter(prefix="/irzplus", tags=["irzplus-mock"])


def wymagany_token(authorization: str | None = Header(default=None)) -> str:
    """Zależność autoryzacyjna — sprawdza nagłówek Authorization: Bearer <token>."""
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Brak nagłówka autoryzacji Bearer.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    token = authorization.split(" ", 1)[1].strip()
    producent = store.resolve_token(token)
    if producent is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token nieprawidłowy lub wygasł.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return producent


@router.post("/logowanie", response_model=LoginResponse)
def logowanie(body: LoginRequest):
    if not body.login or not body.haslo:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Nieprawidłowy login lub hasło.",
        )
    token, ttl = store.issue_token(body.login)
    return LoginResponse(token=token, wygasaZaSekundy=ttl)


@router.get("/zwierze/api/prod/zwierzetaProducenta", response_model=list[ZwierzeIndywidualneDTO])
def pobierz_zwierzeta_producenta(_producent: str = Depends(wymagany_token)):
    """Pobierz listę zwierząt znanych systemowi IRZ."""
    return store.list_animals()


@router.get("/zwierze/api/prod/zwierzeIndywidualne", response_model=ZwierzeIndywidualneDTO)
def pobierz_dane_zwierzecia(
    numerIdentyfikacyjnyZwierzecia: str,
    _producent: str = Depends(wymagany_token),
):
    animal = store.get_animal(numerIdentyfikacyjnyZwierzecia)
    if animal is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Zwierzę nieznane w IRZ.")
    return animal


def _zarejestruj_zwierze(pozycja: PozycjaZgloszeniaDTO) -> None:
    store.register_animal(
        ZwierzeIndywidualneDTO(
            numerIdentyfikacyjnyZwierzecia=pozycja.numerIdentyfikacyjnyZwierzecia,
            numerSiedzibyStada=pozycja.numerSiedzibyStada,
            plec=pozycja.plec,
            rasa=pozycja.rasa,
            dataUrodzenia=pozycja.dataUrodzenia,
            numerIdentyfikacyjnyMatki=pozycja.numerIdentyfikacyjnyMatki,
        ),
    )


def _rozlicz_zwierze(pozycja: PozycjaZgloszeniaDTO) -> None:
    store.close_animal(pozycja.numerIdentyfikacyjnyZwierzecia)


def _przyjmij(
    dyspozycja: DyspozycjaZgloszenia,
    prefix: str,
    komunikat_ok: str,
    validator: Callable[[IrzplusStore, PozycjaZgloszeniaDTO], list[BladWalidacjiDTO]],
    po_przyjeciu: Callable[[PozycjaZgloszeniaDTO], None] | None = None,
) -> ZlozenieDyspozycjiResponse:
    """Wspólna obsługa zgłoszenia: walidacja per typ, akcja po przyjęciu, numer dokumentu."""
    if not dyspozycja.zgloszenie.pozycje:
        return ZlozenieDyspozycjiResponse(
            komunikat="Zgłoszenie odrzucone — brak pozycji.",
            bledy=[BladWalidacjiDTO(kod="ERR_BRAK_POZYCJI", opis="Zgłoszenie nie zawiera żadnej pozycji.")],
        )

    bledy: list[BladWalidacjiDTO] = []
    for pozycja in dyspozycja.zgloszenie.pozycje:
        bledy.extend(validator(store, pozycja))

    if bledy:
        return ZlozenieDyspozycjiResponse(komunikat="Zgłoszenie odrzucone — błędy walidacji.", bledy=bledy)

    if po_przyjeciu is not None:
        for pozycja in dyspozycja.zgloszenie.pozycje:
            po_przyjeciu(pozycja)

    numer = store.next_document_number(prefix)
    store.add_document(numer, prefix, dyspozycja.zgloszenie.pozycje)
    return ZlozenieDyspozycjiResponse(numerDokumentu=numer, komunikat=f"{komunikat_ok} Nadano numer dokumentu {numer}.")


@router.post("/dokument/api/prod/zgloszenieUrodzenia", response_model=ZlozenieDyspozycjiResponse)
def zglos_urodzenie(dyspozycja: DyspozycjaZgloszenia, _p: str = Depends(wymagany_token)):
    return _przyjmij(dyspozycja, "URO", "Przyjęto zgłoszenie urodzenia.", validate_urodzenie, _zarejestruj_zwierze)


@router.post("/dokument/api/prod/zgloszeniePrzybycia", response_model=ZlozenieDyspozycjiResponse)
def zglos_przybycie(dyspozycja: DyspozycjaZgloszenia, _p: str = Depends(wymagany_token)):
    return _przyjmij(dyspozycja, "PRZYB", "Przyjęto zgłoszenie przybycia.", validate_przybycie, _zarejestruj_zwierze)


@router.post("/dokument/api/prod/zgloszenieUbycia", response_model=ZlozenieDyspozycjiResponse)
def zglos_ubycie(dyspozycja: DyspozycjaZgloszenia, _p: str = Depends(wymagany_token)):
    return _przyjmij(dyspozycja, "UBYC", "Przyjęto zgłoszenie ubycia.", validate_ubycie, _rozlicz_zwierze)


@router.post("/dokument/api/prod/zgloszeniePadniecia", response_model=ZlozenieDyspozycjiResponse)
def zglos_padniecie(dyspozycja: DyspozycjaZgloszenia, _p: str = Depends(wymagany_token)):
    return _przyjmij(dyspozycja, "PAD", "Przyjęto zgłoszenie padnięcia.", validate_padniecie, _rozlicz_zwierze)
