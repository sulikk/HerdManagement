"""Pętla agentowa asystenta hodowcy.

Model dostaje zestaw narzędzi (``tools.py``) i prowadzi rozmowę po polsku.
Kluczowa zasada projektu: **agent proponuje — użytkownik zatwierdza**. Narzędzia
odczytowe wykonują się od razu, a każde narzędzie zapisujące zatrzymuje pętlę i
wraca do aplikacji jako propozycja akcji. Dopiero decyzja użytkownika
(``resolve_pending``) wznawia rozmowę. Egzekwuje to backend, nie prompt — model
nie ma technicznej możliwości zapisania czegokolwiek bez zgody.
"""
import json
import logging
from dataclasses import dataclass, field
from datetime import date, datetime
from typing import Any

from sqlalchemy.orm import Session

from app.assistant import tools
from app.assistant.conversation import Conversation, ExecutedAction, PendingAction, store
from app.assistant.llm import LlmClient

logger = logging.getLogger(__name__)

MAX_ITERATIONS = 8  # zabezpieczenie przed zapętleniem modelu na narzędziach

SYSTEM_PROMPT = """\
Jesteś asystentem polskiego hodowcy bydła wbudowanym w aplikację do zarządzania stadem.
Rozmawiasz po polsku, zwięźle i konkretnie, językiem hodowcy (kolczyk, siedziba stada,
karencja, zgłoszenie do IRZplus).

Dzisiejsza data: {today}.

Twoje możliwości:
- odczytujesz ewidencję gospodarstwa narzędziami (zwierzęta, zdarzenia, leczenia,
  statystyki, terminy zgłoszeń, karencje),
- proponujesz zapisy w ewidencji (nowe zwierzę, zdarzenie, leczenie).

Zasady:
1. Zanim odpowiesz na pytanie o dane stada, sprawdź je narzędziem. Nie zgaduj i nie
   wymyślaj numerów kolczyków, dat ani rozpoznań.
2. Narzędzia zapisujące wymagają zgody użytkownika — aplikacja pokaże mu pytanie i
   dopiero jego decyzja uruchamia zapis. Nigdy nie twierdź, że coś zapisałeś, dopóki
   nie dostaniesz wyniku narzędzia.
3. Nie wywołuj narzędzia zapisującego, jeśli brakuje wymaganych danych (np. numeru
   siedziby stada partnera przy przybyciu albo ubyciu) — najpierw dopytaj użytkownika.
4. Zdarzenie trzeba zgłosić do IRZplus w ciągu 7 dni od jego wystąpienia. Jeśli widzisz
   zdarzenia po terminie lub bliskie terminu, powiedz o tym wprost.
5. Zapisanie zdarzenia w ewidencji to nie to samo co zgłoszenie do IRZplus. Wysyłkę
   zgłoszenia uruchamia użytkownik z karty zwierzęcia w aplikacji — przypomnij o tym.
6. Nie udzielasz porad weterynaryjnych ani prawnych zastępujących lekarza weterynarii
   czy ARiMR. Możesz wyjaśniać obowiązki hodowcy i przygotowywać dane.
7. Odpowiadaj tekstem bez formatowania markdown — trafia on prosto do okna czatu.\
"""

DECLINED_RESULT = (
    "Użytkownik nie zgodził się na wykonanie tej akcji. Nic nie zostało zapisane. "
    "Potwierdź to krótko i zapytaj, co zrobić zamiast tego."
)
DEFERRED_RESULT = "Nie wykonano — najpierw trzeba rozstrzygnąć poprzednią propozycję akcji."
LOOP_LIMIT_REPLY = (
    "Nie udało mi się dokończyć tej operacji — rozmowa przekroczyła limit kroków. "
    "Spróbuj zadać pytanie prościej albo podziel je na mniejsze."
)
REFUSAL_REPLY = "Nie mogę pomóc w tej sprawie. Zapytaj o coś związanego z prowadzeniem stada."


class PendingActionError(RuntimeError):
    """Próba wykonania operacji niezgodnej ze stanem rozmowy."""


@dataclass
class TurnResult:
    """Wynik jednej tury rozmowy zwracany do aplikacji mobilnej."""

    reply: str
    pending: PendingAction | None = None
    executed: list[ExecutedAction] = field(default_factory=list)


def _attr(block: Any, name: str, default: Any = None) -> Any:
    """Czyta pole bloku treści niezależnie od tego, czy to obiekt SDK, czy słownik."""
    if isinstance(block, dict):
        return block.get(name, default)
    return getattr(block, name, default)


def _text_of(content: Any) -> str:
    parts = [
        str(_attr(block, "text", ""))
        for block in content or []
        if _attr(block, "type") == "text"
    ]
    return "\n".join(p for p in parts if p).strip()


def _tool_result(tool_use_id: str, payload: Any, is_error: bool = False) -> dict[str, Any]:
    content = payload if isinstance(payload, str) else json.dumps(payload, ensure_ascii=False, default=str)
    block: dict[str, Any] = {
        "type": "tool_result",
        "tool_use_id": tool_use_id,
        "content": content,
    }
    if is_error:
        block["is_error"] = True
    return block


class Assistant:
    """Orkiestrator rozmowy: składa żądania do modelu i wykonuje narzędzia."""

    def __init__(self, llm: LlmClient, today: date | None = None) -> None:
        self._llm = llm
        self._today = today

    # --- API publiczne --------------------------------------------------

    def send(self, db: Session, conversation: Conversation, message: str) -> TurnResult:
        if conversation.pending is not None:
            raise PendingActionError(
                "Rozmowa czeka na decyzję w sprawie zaproponowanej akcji. "
                "Zatwierdź ją albo odrzuć przed wysłaniem kolejnej wiadomości."
            )
        conversation.messages.append({"role": "user", "content": message})
        conversation.touch()
        return self._run(db, conversation)

    def resolve_pending(
        self,
        db: Session,
        conversation: Conversation,
        approved: bool,
        comment: str | None = None,
    ) -> TurnResult:
        pending = conversation.pending
        if pending is None:
            raise PendingActionError("Ta rozmowa nie czeka na potwierdzenie żadnej akcji.")

        executed: list[ExecutedAction] = []
        results = list(pending.prepared_results)
        if approved:
            result_block, action = self._execute(db, pending)
        else:
            result_block = _tool_result(pending.tool_use_id, DECLINED_RESULT)
            action = ExecutedAction(
                name=pending.name,
                params=pending.params,
                description=pending.description,
                approved=False,
                ok=False,
                summary="Użytkownik odrzucił akcję.",
            )
        store.record(conversation, action)
        executed.append(action)
        results.append(result_block)
        results.extend(_tool_result(tid, DEFERRED_RESULT, is_error=True) for tid in pending.deferred_ids)

        content: list[Any] = list(results)
        if comment:
            content.append({"type": "text", "text": f"Komentarz użytkownika: {comment}"})
        conversation.messages.append({"role": "user", "content": content})
        conversation.pending = None
        conversation.touch()

        result = self._run(db, conversation)
        result.executed = executed + result.executed
        return result

    # --- Wnętrze --------------------------------------------------------

    def _run(self, db: Session, conversation: Conversation) -> TurnResult:
        executed: list[ExecutedAction] = []
        today = self._today or datetime.now().date()

        for _ in range(MAX_ITERATIONS):
            response = self._llm.create_message(
                system=SYSTEM_PROMPT.format(today=today.isoformat()),
                messages=conversation.messages,
                tools=tools.TOOL_DEFINITIONS,
            )
            content = _attr(response, "content") or []
            conversation.messages.append({"role": "assistant", "content": content})
            conversation.touch()

            stop_reason = _attr(response, "stop_reason")
            if stop_reason == "refusal":
                return TurnResult(_text_of(content) or REFUSAL_REPLY, None, executed)
            if stop_reason != "tool_use":
                return TurnResult(_text_of(content), None, executed)

            results, pending = self._handle_tool_calls(db, conversation, content, today)
            if pending is not None:
                conversation.pending = pending
                return TurnResult(_text_of(content), pending, executed)
            conversation.messages.append({"role": "user", "content": results})

        logger.warning("Asystent przekroczył limit %s iteracji narzędzi", MAX_ITERATIONS)
        return TurnResult(LOOP_LIMIT_REPLY, None, executed)

    def _handle_tool_calls(
        self,
        db: Session,
        conversation: Conversation,
        content: Any,
        today: date,
    ) -> tuple[list[dict[str, Any]], PendingAction | None]:
        """Wykonuje narzędzia odczytowe; pierwsze zapisujące zwraca jako propozycję."""
        results: list[dict[str, Any]] = []
        pending: PendingAction | None = None

        for block in content or []:
            if _attr(block, "type") != "tool_use":
                continue
            tool_use_id = _attr(block, "id")
            name = _attr(block, "name")
            params = _attr(block, "input") or {}

            if pending is not None:
                pending.deferred_ids.append(tool_use_id)
                continue

            if name in tools.WRITE_TOOLS:
                pending = PendingAction(
                    tool_use_id=tool_use_id,
                    name=name,
                    params=dict(params),
                    description=tools.describe_action(name, params),
                    prepared_results=results,
                )
                continue

            try:
                payload = tools.execute_tool(db, name, params, today)
                results.append(_tool_result(tool_use_id, payload))
            except tools.ToolError as exc:
                results.append(_tool_result(tool_use_id, str(exc), is_error=True))
            except Exception:  # pragma: no cover - nieoczekiwany błąd narzędzia
                logger.exception("Błąd narzędzia %s", name)
                results.append(
                    _tool_result(tool_use_id, "Błąd wewnętrzny narzędzia.", is_error=True)
                )

        return results, pending

    def _execute(self, db: Session, pending: PendingAction) -> tuple[dict[str, Any], ExecutedAction]:
        today = self._today or datetime.now().date()
        try:
            payload = tools.execute_tool(db, pending.name, pending.params, today)
        except tools.ToolError as exc:
            action = ExecutedAction(
                name=pending.name,
                params=pending.params,
                description=pending.description,
                approved=True,
                ok=False,
                summary=str(exc),
            )
            return _tool_result(pending.tool_use_id, str(exc), is_error=True), action
        except Exception as exc:  # pragma: no cover - nieoczekiwany błąd zapisu
            logger.exception("Błąd zapisu narzędziem %s", pending.name)
            db.rollback()
            action = ExecutedAction(
                name=pending.name,
                params=pending.params,
                description=pending.description,
                approved=True,
                ok=False,
                summary=f"Błąd wewnętrzny: {exc}",
            )
            return _tool_result(pending.tool_use_id, "Błąd wewnętrzny zapisu.", is_error=True), action

        action = ExecutedAction(
            name=pending.name,
            params=pending.params,
            description=pending.description,
            approved=True,
            ok=True,
            summary="Zapisano w ewidencji.",
        )
        logger.info("Asystent wykonał akcję %s: %s", pending.name, pending.params)
        return _tool_result(pending.tool_use_id, payload), action

