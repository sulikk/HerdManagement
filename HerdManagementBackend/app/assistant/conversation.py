"""Stan rozmów z asystentem przechowywany w pamięci.

Messages API jest bezstanowe, dlatego pełna historia (wraz z blokami narzędziowymi)
jest przechowywana po stronie serwera, a aplikacja mobilna posługuje się wyłącznie
identyfikatorem rozmowy. Stan nie jest utrwalany i jest tracony przy restarcie procesu.
"""
import secrets
import threading
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Any

CONVERSATION_TTL = timedelta(hours=2)
MAX_CONVERSATIONS = 50
MAX_AUDIT_ENTRIES = 200


def _now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class PendingAction:
    """Akcja zapisująca zaproponowana przez model i czekająca na decyzję użytkownika."""

    tool_use_id: str
    name: str
    params: dict[str, Any]
    description: str
    # Wyniki narzędzi odczytowych z tej samej tury; są przekazywane modelowi razem
    # z wynikiem akcji, ponieważ każdy blok tool_use wymaga odpowiedzi.
    prepared_results: list[dict[str, Any]] = field(default_factory=list)
    deferred_ids: list[str] = field(default_factory=list)


@dataclass
class ExecutedAction:
    """Wpis dziennika akcji wykonanej przez asystenta po decyzji użytkownika."""

    name: str
    params: dict[str, Any]
    description: str
    approved: bool
    ok: bool
    summary: str
    at: datetime = field(default_factory=_now)


@dataclass
class Conversation:
    id: str
    messages: list[dict[str, Any]] = field(default_factory=list)
    pending: PendingAction | None = None
    audit: list[ExecutedAction] = field(default_factory=list)
    created_at: datetime = field(default_factory=_now)
    updated_at: datetime = field(default_factory=_now)

    def touch(self) -> None:
        self.updated_at = _now()


class ConversationStore:
    """Rejestr rozmów w pamięci wraz z globalnym dziennikiem akcji."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._conversations: dict[str, Conversation] = {}
        self._audit: list[ExecutedAction] = []

    def create(self) -> Conversation:
        conversation = Conversation(id=secrets.token_urlsafe(12))
        with self._lock:
            self._prune()
            self._conversations[conversation.id] = conversation
        return conversation

    def get(self, conversation_id: str) -> Conversation | None:
        with self._lock:
            self._prune()
            return self._conversations.get(conversation_id)

    def get_or_create(self, conversation_id: str | None) -> Conversation:
        if conversation_id:
            existing = self.get(conversation_id)
            if existing is not None:
                return existing
        return self.create()

    def delete(self, conversation_id: str) -> bool:
        with self._lock:
            return self._conversations.pop(conversation_id, None) is not None

    def record(self, conversation: Conversation, action: ExecutedAction) -> None:
        conversation.audit.append(action)
        with self._lock:
            self._audit.append(action)
            del self._audit[:-MAX_AUDIT_ENTRIES]

    def audit(self) -> list[ExecutedAction]:
        with self._lock:
            return list(self._audit)

    def _prune(self) -> None:
        """Usuwa rozmowy wygasłe, a po przekroczeniu limitu — najstarsze."""
        deadline = _now() - CONVERSATION_TTL
        for key in [k for k, c in self._conversations.items() if c.updated_at < deadline]:
            del self._conversations[key]
        if len(self._conversations) > MAX_CONVERSATIONS:
            oldest = sorted(self._conversations.values(), key=lambda c: c.updated_at)
            for conversation in oldest[: len(self._conversations) - MAX_CONVERSATIONS]:
                del self._conversations[conversation.id]


store = ConversationStore()
