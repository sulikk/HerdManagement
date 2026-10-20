"""Weryfikacja ogłoszenia w rejestrze IRZplus (wymaganie F14).

Odznaka „dane potwierdzone w IRZplus” oznacza, że numer kolczyka istnieje w rejestrze,
zwierzę nie zostało w nim rozliczone (ubycie lub padnięcie), a dane ogłoszenia są
zgodne z danymi rejestru.

Porównanie jest asymetryczne: pole puste w rejestrze nie unieważnia odznaki, natomiast
pole o odmiennej wartości — tak. Każda rozbieżność jest opisywana komunikatem.
"""
from dataclasses import dataclass, field

from app.irzplus.schemas import ZwierzeIndywidualneDTO
from app.models import Animal, Sex

# Słownik płci zgodny z symulatorem IRZplus (`PLEC_DOZWOLONE` w app/irzplus/service.py)
# oraz z mapowaniem w aplikacji mobilnej (`Sex.toIrz()`).
SEX_TO_IRZ = {Sex.FEMALE: "samica", Sex.MALE: "samiec", Sex.OTHER: "nieokreslona"}


@dataclass
class VerificationResult:
    """Wynik porównania karty zwierzęcia z rejestrem."""

    verified: bool
    message: str | None = None
    discrepancies: list[str] = field(default_factory=list)


def verify_animal(animal: Animal, registry: ZwierzeIndywidualneDTO | None, closed: bool) -> VerificationResult:
    """Porównuje kartę zwierzęcia z wpisem w rejestrze IRZplus.

    :param registry: wpis rejestru lub ``None``, gdy numer jest nieznany.
    :param closed: ``True``, gdy zwierzę zostało rozliczone w rejestrze i nie może
        być przedmiotem sprzedaży.
    """
    if registry is None:
        return VerificationResult(
            verified=False,
            message=(
                f"Kolczyk {animal.ear_tag} nie figuruje w rejestrze IRZplus. "
                "Zgłoś najpierw urodzenie albo przybycie zwierzęcia."
            ),
        )

    if closed:
        return VerificationResult(
            verified=False,
            message=(
                f"Zwierzę {animal.ear_tag} jest w rejestrze IRZplus rozliczone "
                "(zgłoszono ubycie lub padnięcie) i nie może być przedmiotem sprzedaży."
            ),
        )

    discrepancies = _compare(animal, registry)
    if discrepancies:
        return VerificationResult(
            verified=False,
            message="Dane ogłoszenia różnią się od rejestru IRZplus: "
            + "; ".join(discrepancies)
            + ".",
            discrepancies=discrepancies,
        )

    return VerificationResult(verified=True, message="Dane zgodne z rejestrem IRZplus.")


def _compare(animal: Animal, registry: ZwierzeIndywidualneDTO) -> list[str]:
    """Zwraca listę rozbieżności; pola nieuzupełnione w rejestrze są pomijane."""
    found: list[str] = []

    if registry.dataUrodzenia and animal.birth_date and registry.dataUrodzenia != animal.birth_date:
        found.append(
            f"data urodzenia (karta: {animal.birth_date}, rejestr: {registry.dataUrodzenia})"
        )

    expected_sex = SEX_TO_IRZ.get(animal.sex)
    if registry.plec and expected_sex and not _same_text(registry.plec, expected_sex):
        found.append(f"płeć (karta: {expected_sex}, rejestr: {registry.plec})")

    if registry.rasa and animal.breed and not _same_text(registry.rasa, animal.breed):
        found.append(f"rasa (karta: {animal.breed}, rejestr: {registry.rasa})")

    if registry.numerSiedzibyStada and animal.herd_id and registry.numerSiedzibyStada != animal.herd_id:
        found.append(
            f"siedziba stada (karta: {animal.herd_id}, rejestr: {registry.numerSiedzibyStada})"
        )

    if (
        registry.numerIdentyfikacyjnyMatki
        and animal.mother_ear_tag
        and registry.numerIdentyfikacyjnyMatki != animal.mother_ear_tag
    ):
        found.append(
            f"numer matki (karta: {animal.mother_ear_tag}, "
            f"rejestr: {registry.numerIdentyfikacyjnyMatki})"
        )

    return found


def _same_text(left: str, right: str) -> bool:
    """Porównuje teksty bez uwzględnienia wielkości liter i nadmiarowych spacji."""
    return left.strip().casefold() == right.strip().casefold()
