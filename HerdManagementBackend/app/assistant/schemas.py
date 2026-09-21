"""Kontrakt REST asystenta — to, co widzi aplikacja mobilna."""
from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field

from app.assistant.conversation import ExecutedAction, PendingAction


class ChatRequest(BaseModel):
    conversation_id: str | None = Field(
        default=None,
        description="Identyfikator rozmowy. Pusty zaczyna nową rozmowę.",
    )
    message: str = Field(min_length=1, max_length=2000)


class ConfirmRequest(BaseModel):
    conversation_id: str
    approved: bool = Field(description="True = wykonaj zaproponowaną akcję, False = odrzuć.")
    comment: str | None = Field(default=None, max_length=500)


class PendingActionRead(BaseModel):
    """Propozycja akcji zapisującej czekająca na decyzję użytkownika."""

    tool_use_id: str
    name: str
    description: str
    params: dict[str, Any]

    @classmethod
    def from_action(cls, action: PendingAction) -> "PendingActionRead":
        return cls(
            tool_use_id=action.tool_use_id,
            name=action.name,
            description=action.description,
            params=action.params,
        )


class ExecutedActionRead(BaseModel):
    """Wpis dziennika akcji asystenta (audyt zmian w danych)."""

    name: str
    description: str
    params: dict[str, Any]
    approved: bool
    ok: bool
    summary: str
    at: datetime

    @classmethod
    def from_action(cls, action: ExecutedAction) -> "ExecutedActionRead":
        return cls(
            name=action.name,
            description=action.description,
            params=action.params,
            approved=action.approved,
            ok=action.ok,
            summary=action.summary,
            at=action.at,
        )


class ChatResponse(BaseModel):
    conversation_id: str
    reply: str
    state: Literal["answer", "awaiting_confirmation"]
    pending_action: PendingActionRead | None = None
    executed_actions: list[ExecutedActionRead] = Field(default_factory=list)


class HistoryMessage(BaseModel):
    """Uproszczony zapis rozmowy dla widoku czatu (bez bloków narzędziowych)."""

    role: Literal["user", "assistant"]
    text: str


class AssistantStatus(BaseModel):
    configured: bool = Field(description="Czy backend ma klucz do modelu językowego.")
    model: str
    tools: list[str]
