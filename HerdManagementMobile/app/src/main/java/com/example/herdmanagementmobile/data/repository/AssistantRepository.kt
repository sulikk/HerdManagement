package com.example.herdmanagementmobile.data.repository

import com.example.herdmanagementmobile.data.remote.assistant.AssistantApiService
import com.example.herdmanagementmobile.data.remote.assistant.ChatRequestDto
import com.example.herdmanagementmobile.data.remote.assistant.ChatResponseDto
import com.example.herdmanagementmobile.data.remote.assistant.ConfirmRequestDto
import com.example.herdmanagementmobile.data.remote.assistant.PendingActionDto
import com.example.herdmanagementmobile.domain.model.AssistantAction
import com.example.herdmanagementmobile.domain.model.AssistantActionResult
import com.example.herdmanagementmobile.domain.model.AssistantTurn
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rozmowa z asystentem LLM. Stan rozmowy trzyma backend — tutaj wystarczy
 * identyfikator zwrócony przy pierwszej wiadomości. Błędy sieci i serwera
 * tłumaczone są na komunikaty dla hodowcy.
 */
@Singleton
class AssistantRepository @Inject constructor(
    private val api: AssistantApiService,
) {
    suspend fun isConfigured(): Result<Boolean> = call { api.status().configured }

    suspend fun send(conversationId: String?, message: String): Result<AssistantTurn> =
        call { api.chat(ChatRequestDto(conversationId, message)).toDomain() }

    suspend fun confirm(
        conversationId: String,
        approved: Boolean,
        comment: String? = null,
    ): Result<AssistantTurn> =
        call { api.confirm(ConfirmRequestDto(conversationId, approved, comment)).toDomain() }

    private inline fun <T> call(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: IOException) {
        Result.failure(AssistantException("Brak połączenia z serwerem asystenta.", e))
    } catch (e: HttpException) {
        Result.failure(AssistantException(message(e), e))
    }

    private fun message(e: HttpException): String = when (e.code()) {
        404 -> "Rozmowa wygasła. Zacznij nową."
        409 -> "Najpierw rozstrzygnij propozycję asystenta."
        502 -> "Model językowy nie odpowiedział. Spróbuj ponownie."
        503 -> "Asystent nie jest skonfigurowany na serwerze (brak klucza API modelu)."
        else -> "Błąd serwera asystenta (${e.code()})."
    }
}

class AssistantException(message: String, cause: Throwable? = null) : Exception(message, cause)

private fun ChatResponseDto.toDomain(): AssistantTurn = AssistantTurn(
    conversationId = conversationId,
    reply = reply,
    pendingAction = pendingAction?.toDomain(),
    executedActions = executedActions.map {
        AssistantActionResult(
            description = it.description,
            approved = it.approved,
            ok = it.ok,
            summary = it.summary,
        )
    },
)

private fun PendingActionDto.toDomain(): AssistantAction = AssistantAction(
    toolUseId = toolUseId,
    name = name,
    description = description,
    params = params.entries.map { (key, value) ->
        val text = (value as? JsonPrimitive)?.content ?: value.toString()
        key.replace('_', ' ') to text
    },
)
