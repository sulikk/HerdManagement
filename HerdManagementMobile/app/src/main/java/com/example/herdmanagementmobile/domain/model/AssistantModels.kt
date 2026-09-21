package com.example.herdmanagementmobile.domain.model

/** Propozycja akcji asystenta czekająca na zgodę użytkownika. */
data class AssistantAction(
    val toolUseId: String,
    val name: String,
    val description: String,
    val params: List<Pair<String, String>>,
)

/** Skutek akcji po decyzji użytkownika — pokazywany w czacie jako wpis systemowy. */
data class AssistantActionResult(
    val description: String,
    val approved: Boolean,
    val ok: Boolean,
    val summary: String,
)

/** Wynik jednej tury rozmowy. */
data class AssistantTurn(
    val conversationId: String,
    val reply: String,
    val pendingAction: AssistantAction?,
    val executedActions: List<AssistantActionResult>,
)
