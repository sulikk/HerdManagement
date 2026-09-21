"""Cienka warstwa nad SDK Anthropic.

Klucz API żyje wyłącznie po stronie backendu (wymaganie N3 z dokumentacji) —
aplikacja mobilna rozmawia tylko z tym serwerem. Protokół ``LlmClient`` pozwala
podstawić atrapę w testach, więc pętla agentowa jest testowalna bez sieci.
"""
import os
from typing import Any, Protocol

from app.config import settings

# Beta serwerowego fallbacku na odmowę modelu (stop_reason="refusal").
# Domyślnie wyłączony — wymaga konta z dostępem do bety, a w domenie hodowlanej
# odmowy praktycznie nie występują. Włącza się przez LLM_REFUSAL_FALLBACK=true.
REFUSAL_FALLBACK_BETA = "server-side-fallback-2026-07-01"

# Parametr output_config.effort obsluguja modele generacji Opus/Sonnet 5 i nowsze.
# Starsze (np. claude-haiku-4-5, uzywany na czas developmentu ze wzgledu na koszt)
# odrzucaja go bledem 400, wiec dla nich po prostu go nie wysylamy.
EFFORT_MODEL_PREFIXES = ("claude-opus-", "claude-sonnet-5", "claude-fable-", "claude-mythos-")


def supports_effort(model: str) -> bool:
    return model.startswith(EFFORT_MODEL_PREFIXES)


class AssistantUnavailable(RuntimeError):
    """Asystent nie ma skonfigurowanego dostępu do modelu."""


class LlmClient(Protocol):
    """Minimalny kontrakt używany przez pętlę agentową."""

    def create_message(
        self,
        *,
        system: str,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]],
    ) -> Any:
        """Zwraca obiekt z polami ``content`` (lista bloków) i ``stop_reason``."""
        ...


def api_key() -> str:
    """Klucz z ustawień aplikacji albo ze standardowej zmiennej środowiskowej SDK."""
    return settings.llm_api_key or os.environ.get("ANTHROPIC_API_KEY", "")


def is_configured() -> bool:
    return bool(api_key())


class AnthropicLlm:
    """Implementacja ``LlmClient`` na oficjalnym SDK Anthropic."""

    def __init__(self, key: str | None = None) -> None:
        import anthropic  # import lokalny: brak klucza nie powinien blokować startu API

        resolved = key or api_key()
        if not resolved:
            raise AssistantUnavailable(
                "Brak klucza do modelu językowego. Ustaw LLM_API_KEY (albo ANTHROPIC_API_KEY)."
            )
        self._client = anthropic.Anthropic(api_key=resolved)

    def create_message(
        self,
        *,
        system: str,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]],
    ) -> Any:
        params: dict[str, Any] = {
            "model": settings.llm_model,
            "max_tokens": settings.llm_max_tokens,
            "system": system,
            "messages": messages,
            "tools": tools,
            # Jedno narzędzie na turę — dzięki temu zatwierdzanie akcji przez
            # użytkownika zatrzymuje pętlę w jednoznacznym miejscu.
            "tool_choice": {"type": "auto", "disable_parallel_tool_use": True},
        }
        if supports_effort(settings.llm_model):
            params["output_config"] = {"effort": settings.llm_effort}
        if settings.llm_refusal_fallback:
            return self._client.beta.messages.create(
                betas=[REFUSAL_FALLBACK_BETA], fallbacks="default", **params
            )
        return self._client.messages.create(**params)


def build_client() -> LlmClient:
    """Fabryka używana przez router (podmieniana w testach)."""
    return AnthropicLlm()
