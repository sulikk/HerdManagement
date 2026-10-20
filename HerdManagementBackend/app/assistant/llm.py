"""Warstwa dostępu do modelu językowego (SDK Anthropic).

Klucz API jest przechowywany wyłącznie po stronie backendu (wymaganie N3).
Protokół ``LlmClient`` umożliwia zastąpienie modelu atrapą w testach.
"""
import os
from typing import Any, Protocol

from app.config import settings

# Funkcja beta obsługi odmowy modelu (stop_reason="refusal") po stronie serwera.
# Domyślnie wyłączona, ponieważ wymaga dostępu do wersji beta; włączana zmienną
# LLM_REFUSAL_FALLBACK=true.
REFUSAL_FALLBACK_BETA = "server-side-fallback-2026-07-01"

# Parametr output_config.effort jest obsługiwany przez modele Opus/Sonnet 5 i nowsze.
# Starsze modele (np. claude-haiku-4-5) odrzucają go błędem 400, dlatego jest pomijany.
EFFORT_MODEL_PREFIXES = ("claude-opus-", "claude-sonnet-5", "claude-fable-", "claude-mythos-")


def supports_effort(model: str) -> bool:
    return model.startswith(EFFORT_MODEL_PREFIXES)


class AssistantUnavailable(RuntimeError):
    """Brak skonfigurowanego dostępu do modelu językowego."""


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
    """Zwraca klucz API z ustawień aplikacji lub ze standardowej zmiennej środowiskowej SDK."""
    return settings.llm_api_key or os.environ.get("ANTHROPIC_API_KEY", "")


def is_configured() -> bool:
    return bool(api_key())


class AnthropicLlm:
    """Implementacja ``LlmClient`` na oficjalnym SDK Anthropic."""

    def __init__(self, key: str | None = None) -> None:
        import anthropic  # import lokalny, aby brak klucza API nie blokował uruchomienia aplikacji

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
            # Jedno wywołanie narzędzia na turę zapewnia jednoznaczny punkt zatwierdzenia akcji.
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
    """Tworzy klienta modelu; w testach zastępowana atrapą."""
    return AnthropicLlm()
