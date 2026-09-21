from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    database_url: str = "sqlite:///./herd.db"
    cors_origins: list[str] = Field(default_factory=lambda: ["*"])

    # --- Asystent LLM (Zadanie 8) ---
    llm_provider: str = "anthropic"
    llm_api_key: str = ""
    llm_model: str = "claude-opus-5"
    llm_max_tokens: int = 16000
    llm_effort: str = "medium"  # low | medium | high | xhigh | max
    # Serwerowy fallback na odmowę modelu — wymaga konta z dostępem do bety.
    llm_refusal_fallback: bool = False

    irzplus_base_url: str = "https://epuap.gov.pl/irzplus"
    irzplus_client_id: str = ""
    irzplus_client_secret: str = ""


settings = Settings()
