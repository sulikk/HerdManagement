package com.example.herdmanagementmobile.ui.screens.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.repository.AssistantRepository
import com.example.herdmanagementmobile.data.sync.SyncManager
import com.example.herdmanagementmobile.domain.model.AssistantAction
import com.example.herdmanagementmobile.domain.model.AssistantTurn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ChatAuthor { USER, ASSISTANT, SYSTEM }

/**
 * Wydźwięk wpisu systemowego — decyduje o ikonie i kolorze.
 * Wpis o zapisie w ewidencji musi wyglądać inaczej niż odmowa czy błąd.
 */
enum class MessageTone { NEUTRAL, SUCCESS, REJECTED, ERROR }

data class ChatEntry(
    val id: Long,
    val author: ChatAuthor,
    val text: String,
    val tone: MessageTone = MessageTone.NEUTRAL,
)

data class AssistantState(
    val entries: List<ChatEntry> = emptyList(),
    val input: String = "",
    val busy: Boolean = false,
    val pending: AssistantAction? = null,
    val error: String? = null,
) {
    /** Rozmowa jeszcze się nie zaczęła — jest miejsce na podpowiedzi. */
    val fresh: Boolean get() = entries.none { it.author == ChatAuthor.USER }

    /** Dopóki propozycja czeka na decyzję, backend nie przyjmie kolejnej wiadomości. */
    val inputBlocked: Boolean get() = busy || pending != null
}

private const val GREETING =
    "Zapytaj o stado albo poproś o przygotowanie wpisu do ewidencji. " +
        "Każdy zapis pokażę Ci do zatwierdzenia — bez Twojej zgody nic nie trafi do bazy."

/** Gotowe pytania na start — hodowca nie musi zgadywać, co asystent potrafi. */
val SUGGESTED_PROMPTS = listOf(
    "Co muszę zgłosić do IRZplus?",
    "Które sztuki są na karencji?",
    "Ile mam cieląt?",
    "Pokaż statystyki stada",
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val repository: AssistantRepository,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AssistantState(entries = listOf(ChatEntry(0, ChatAuthor.ASSISTANT, GREETING))),
    )
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private var conversationId: String? = null
    private var nextId = 1L

    init {
        // Brak odpowiedzi serwera nie jest tu błędem wartym alertu — zgłosi się
        // przy pierwszej wiadomości. Ostrzegamy tylko o braku klucza do modelu.
        viewModelScope.launch {
            repository.isConfigured().onSuccess { configured ->
                if (!configured) {
                    append(
                        author = ChatAuthor.SYSTEM,
                        text = "Backend nie ma skonfigurowanego klucza do modelu językowego — " +
                            "asystent nie odpowie, dopóki nie ustawisz LLM_API_KEY na serwerze.",
                        tone = MessageTone.ERROR,
                    )
                }
            }
        }
    }

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun send() = ask(_state.value.input)

    /** Wysyła treść wprost — używane też przez podpowiedzi startowe. */
    fun ask(message: String) {
        val text = message.trim()
        if (text.isEmpty() || _state.value.inputBlocked) return
        append(ChatAuthor.USER, text)
        _state.update { it.copy(input = "", busy = true, error = null) }
        viewModelScope.launch {
            repository.send(conversationId, text)
                .onSuccess(::apply)
                .onFailure(::fail)
            _state.update { it.copy(busy = false) }
        }
    }

    /** Decyzja użytkownika w sprawie zaproponowanej akcji (polityka F12 / Zad. 8). */
    fun decide(approved: Boolean) {
        val id = conversationId ?: return
        if (_state.value.pending == null) return
        _state.update { it.copy(pending = null, busy = true, error = null) }
        viewModelScope.launch {
            repository.confirm(id, approved)
                .onSuccess(::apply)
                .onFailure(::fail)
            _state.update { it.copy(busy = false) }
        }
    }

    fun newConversation() {
        conversationId = null
        nextId = 1L
        _state.value = AssistantState(entries = listOf(ChatEntry(0, ChatAuthor.ASSISTANT, GREETING)))
    }

    private fun apply(turn: AssistantTurn) {
        conversationId = turn.conversationId
        turn.executedActions.forEach { action ->
            val tone = when {
                !action.approved -> MessageTone.REJECTED
                action.ok -> MessageTone.SUCCESS
                else -> MessageTone.ERROR
            }
            val prefix = when (tone) {
                MessageTone.REJECTED -> "Odrzucono: "
                MessageTone.SUCCESS -> "Zapisano: "
                else -> "Nie udało się: "
            }
            append(
                author = ChatAuthor.SYSTEM,
                text = prefix + action.description.trimEnd('?') + suffix(action.summary, action.ok),
                tone = tone,
            )
        }
        // Narzędzia asystenta zapisują w bazie backendu, nie w Room. Bez tego
        // zatwierdzony przed chwilą wpis nie pojawiłby się na karcie zwierzęcia.
        if (turn.executedActions.any { it.approved && it.ok }) {
            syncManager.scheduleSync()
        }
        if (turn.reply.isNotBlank()) {
            append(ChatAuthor.ASSISTANT, turn.reply)
        }
        _state.update { it.copy(pending = turn.pendingAction) }
    }

    private fun suffix(summary: String, ok: Boolean): String = if (ok) "" else " — $summary"

    private fun fail(error: Throwable) {
        _state.update { it.copy(error = message(error)) }
    }

    private fun message(error: Throwable?): String =
        error?.message ?: "Nie udało się połączyć z asystentem."

    private fun append(
        author: ChatAuthor,
        text: String,
        tone: MessageTone = MessageTone.NEUTRAL,
    ) {
        val entry = ChatEntry(nextId++, author, text, tone)
        _state.update { it.copy(entries = it.entries + entry) }
    }
}
