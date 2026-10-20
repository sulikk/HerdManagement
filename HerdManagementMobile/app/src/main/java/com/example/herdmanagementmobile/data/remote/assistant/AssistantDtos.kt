package com.example.herdmanagementmobile.data.remote.assistant

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Żądanie wiadomości do asystenta LLM. Historia rozmowy i klucz API modelu
 * pozostają po stronie backendu (wymaganie N3).
 */
@Serializable
data class ChatRequestDto(
    @SerialName("conversation_id") val conversationId: String? = null,
    val message: String,
)

@Serializable
data class ConfirmRequestDto(
    @SerialName("conversation_id") val conversationId: String,
    val approved: Boolean,
    val comment: String? = null,
)

/** Propozycja akcji modyfikującej dane, oczekująca na decyzję użytkownika. */
@Serializable
data class PendingActionDto(
    @SerialName("tool_use_id") val toolUseId: String,
    val name: String,
    val description: String,
    val params: JsonObject = JsonObject(emptyMap()),
)

/** Akcja wykonana lub odrzucona po decyzji użytkownika. */
@Serializable
data class ExecutedActionDto(
    val name: String,
    val description: String,
    val approved: Boolean,
    val ok: Boolean,
    val summary: String,
)

@Serializable
data class ChatResponseDto(
    @SerialName("conversation_id") val conversationId: String,
    val reply: String,
    /** Stan rozmowy: `answer` lub `awaiting_confirmation`. */
    val state: String,
    @SerialName("pending_action") val pendingAction: PendingActionDto? = null,
    @SerialName("executed_actions") val executedActions: List<ExecutedActionDto> = emptyList(),
)

@Serializable
data class AssistantStatusDto(
    val configured: Boolean,
    val model: String,
    val tools: List<String> = emptyList(),
)
