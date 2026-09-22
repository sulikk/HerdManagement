package com.example.herdmanagementmobile.ui.screens.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.repository.EventRepository
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.ComplianceItem
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.validation.HoldingNumber
import com.example.herdmanagementmobile.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class EventFormState(
    val type: EventType = EventType.BIRTH,
    val eventDate: LocalDate = LocalDate.now(),
    val notes: String = "",
    val partnerHolding: String = "",
    val partnerError: String? = null,
    val dateError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
) {
    /** Przybycie/ubycie wymaga numeru siedziby partnera (skąd/dokąd). */
    val requiresPartner: Boolean
        get() = type == EventType.ARRIVAL || type == EventType.DEPARTURE

    /** Ustawowy termin zgłoszenia: data zdarzenia + 7 dni. */
    val deadline: LocalDate get() = eventDate.plusDays(ComplianceItem.REPORTING_DAYS)

    val daysToDeadline: Long get() = ChronoUnit.DAYS.between(LocalDate.now(), deadline)
}

@HiltViewModel
class EventFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val eventRepository: EventRepository,
) : ViewModel() {

    private val animalId: Long = savedStateHandle.get<Long>(Route.EventForm.ARG_ANIMAL) ?: -1L

    private val _state = MutableStateFlow(EventFormState())
    val state: StateFlow<EventFormState> = _state.asStateFlow()

    fun onTypeChange(type: EventType) = _state.update { it.copy(type = type, partnerError = null) }
    fun onDateChange(date: LocalDate) = _state.update { it.copy(eventDate = date, dateError = null) }
    fun onNotesChange(value: String) = _state.update { it.copy(notes = value) }
    fun onPartnerHoldingChange(value: String) =
        _state.update { it.copy(partnerHolding = value.uppercase(), partnerError = null) }

    /** Sprawdza formularz przed pokazaniem pytania o zgłoszenie do IRZ. */
    fun validate(): Boolean {
        if (animalId <= 0) return false
        val s = _state.value
        // Zdarzenie to zapis tego, co już się stało — rejestr odrzuca daty przyszłe
        // (ERR_DATA_PRZYSZLA), więc nie ma po co wysyłać takiego zgłoszenia.
        if (s.eventDate.isAfter(LocalDate.now())) {
            _state.update { it.copy(dateError = "Data zdarzenia nie może być z przyszłości.") }
            return false
        }
        if (s.requiresPartner && !HoldingNumber.isValidHolding(s.partnerHolding)) {
            val label = if (s.type == EventType.ARRIVAL) "z której zwierzę przybyło" else "do której zwierzę ubyło"
            _state.update {
                it.copy(partnerError = "Podaj numer siedziby, $label (PL + 9 cyfr).")
            }
            return false
        }
        return true
    }

    /** [submitToIrz] — użytkownik potwierdził w dialogu zgłoszenie do IRZplus. */
    fun save(submitToIrz: Boolean, onSuccess: () -> Unit) {
        if (!validate()) return
        val s = _state.value

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveError = null) }
            try {
                eventRepository.add(
                    AnimalEvent(
                        animalId = animalId,
                        type = s.type,
                        eventDate = s.eventDate,
                        notes = s.notes.ifBlank { null },
                        partnerHoldingNumber = s.partnerHolding.ifBlank { null }
                            .takeIf { s.requiresPartner },
                    ),
                    submitToIrz = submitToIrz,
                )
                onSuccess()
            } catch (e: Exception) {
                _state.update {
                    it.copy(saveError = "Nie udało się zapisać zdarzenia.", isSaving = false)
                }
            }
        }
    }
}
