package com.example.herdmanagementmobile.ui.screens.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.repository.EventRepository
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.ComplianceItem
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.validation.EventConflict
import com.example.herdmanagementmobile.domain.validation.EventConflicts
import com.example.herdmanagementmobile.domain.validation.HoldingNumber
import com.example.herdmanagementmobile.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    /**
     * Sprzeczność z historią zwierzęcia lub `null`, gdy jej brak lub historia nie została
     * jeszcze wczytana (zob. [blocked]).
     */
    val conflict: EventConflict? = null,
) {

    /** Określa, czy zapis jest niemożliwy z powodu sprzeczności blokującej. */
    val blocked: Boolean get() = conflict?.blocking == true
    /** Określa, czy wymagany jest numer siedziby stada kontrahenta (przybycie, ubycie). */
    val requiresPartner: Boolean
        get() = type == EventType.ARRIVAL || type == EventType.DEPARTURE

    /** Ustawowy termin zgłoszenia: data zdarzenia powiększona o 7 dni. */
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

    /** Historia zdarzeń zwierzęcia wykorzystywana do wykrywania sprzeczności. */
    private var existing: List<AnimalEvent> = emptyList()

    init {
        if (animalId > 0) {
            viewModelScope.launch {
                eventRepository.observeForAnimal(animalId).collectLatest { events ->
                    existing = events
                    _state.update { it.copy(conflict = EventConflicts.detect(it.type, events)) }
                }
            }
        }
    }

    fun onTypeChange(type: EventType) = _state.update {
        it.copy(
            type = type,
            partnerError = null,
            conflict = EventConflicts.detect(type, existing),
        )
    }
    fun onDateChange(date: LocalDate) = _state.update { it.copy(eventDate = date, dateError = null) }
    fun onNotesChange(value: String) = _state.update { it.copy(notes = value) }
    fun onPartnerHoldingChange(value: String) =
        _state.update { it.copy(partnerHolding = value.uppercase(), partnerError = null) }

    /** Waliduje formularz przed wyświetleniem pytania o zgłoszenie do IRZplus. */
    fun validate(): Boolean {
        if (animalId <= 0) return false
        val s = _state.value
        // Ponowna weryfikacja blokady: historia mogła zmienić się w tle po synchronizacji.
        if (s.blocked) return false
        // IRZplus odrzuca daty przyszłe (ERR_DATA_PRZYSZLA).
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

    /**
     * Zapisuje zdarzenie.
     *
     * @param submitToIrz `true`, gdy użytkownik potwierdził zgłoszenie do IRZplus.
     */
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
