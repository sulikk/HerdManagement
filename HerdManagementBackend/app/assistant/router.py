"""Endpointy asystenta LLM.

Aplikacja mobilna przesyła wyłącznie treść wiadomości i identyfikator rozmowy.
Akcje zapisujące są zwracane jako ``pending_action`` i wymagają potwierdzenia
przez ``POST /assistant/confirm``.
"""
import logging
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.assistant import llm
from app.assistant.agent import Assistant, PendingActionError, TurnResult
from app.assistant.conversation import Conversation, store
from app.assistant.schemas import (
    AssistantStatus,
    ChatRequest,
    ChatResponse,
    ConfirmRequest,
    ExecutedActionRead,
    HistoryMessage,
    PendingActionRead,
)
from app.assistant.tools import TOOL_DEFINITIONS
from app.config import settings
from app.database import get_db

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/assistant", tags=["assistant"])


def get_assistant() -> Assistant:
    """Zależność FastAPI; w testach zastępowana przez ``dependency_overrides``."""
    try:
        return Assistant(llm.build_client())
    except llm.AssistantUnavailable as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exc)) from exc


def _conversation(conversation_id: str) -> Conversation:
    conversation = store.get(conversation_id)
    if conversation is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Nie znaleziono rozmowy (mogła wygasnąć). Zacznij nową.",
        )
    return conversation


def _response(conversation: Conversation, result: TurnResult) -> ChatResponse:
    return ChatResponse(
        conversation_id=conversation.id,
        reply=result.reply,
        state="awaiting_confirmation" if result.pending else "answer",
        pending_action=PendingActionRead.from_action(result.pending) if result.pending else None,
        executed_actions=[ExecutedActionRead.from_action(a) for a in result.executed],
    )


def _run(call) -> TurnResult:
    """Uruchamia turę rozmowy, odwzorowując wyjątki na kody HTTP."""
    try:
        return call()
    except PendingActionError as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(exc)) from exc
    except llm.AssistantUnavailable as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception("Błąd rozmowy z modelem językowym")
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Błąd komunikacji z modelem językowym: {exc}",
        ) from exc


@router.get("/status", response_model=AssistantStatus)
def assistant_status():
    return AssistantStatus(
        configured=llm.is_configured(),
        model=settings.llm_model,
        tools=[t["name"] for t in TOOL_DEFINITIONS],
    )


@router.post("/chat", response_model=ChatResponse)
def chat(
    payload: ChatRequest,
    db: Session = Depends(get_db),
    assistant: Assistant = Depends(get_assistant),
):
    if payload.conversation_id:
        conversation = _conversation(payload.conversation_id)
    else:
        conversation = store.create()
    result = _run(lambda: assistant.send(db, conversation, payload.message))
    return _response(conversation, result)


@router.post("/confirm", response_model=ChatResponse)
def confirm(
    payload: ConfirmRequest,
    db: Session = Depends(get_db),
    assistant: Assistant = Depends(get_assistant),
):
    conversation = _conversation(payload.conversation_id)
    result = _run(
        lambda: assistant.resolve_pending(db, conversation, payload.approved, payload.comment)
    )
    return _response(conversation, result)


@router.get("/conversations/{conversation_id}", response_model=list[HistoryMessage])
def history(conversation_id: str):
    conversation = _conversation(conversation_id)
    entries: list[HistoryMessage] = []
    for message in conversation.messages:
        role = message.get("role")
        if role not in ("user", "assistant"):
            continue
        text = _plain_text(message.get("content"))
        if text:
            entries.append(HistoryMessage(role=role, text=text))
    return entries


@router.delete("/conversations/{conversation_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_conversation(conversation_id: str):
    if not store.delete(conversation_id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Nie znaleziono rozmowy.")


@router.get("/audit", response_model=list[ExecutedActionRead])
def audit():
    """Dziennik akcji zapisujących proponowanych przez asystenta wraz z decyzją użytkownika."""
    return [ExecutedActionRead.from_action(a) for a in store.audit()]


def _plain_text(content: Any) -> str:
    """Zwraca tekst wiadomości z pominięciem bloków narzędziowych."""
    if isinstance(content, str):
        return content.strip()
    parts: list[str] = []
    for block in content or []:
        block_type = block.get("type") if isinstance(block, dict) else getattr(block, "type", None)
        if block_type != "text":
            continue
        text = block.get("text") if isinstance(block, dict) else getattr(block, "text", "")
        if text:
            parts.append(str(text))
    return "\n".join(parts).strip()
