package com.example.herdmanagementmobile.ui.screens.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.repository.EventRepository
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class EventFormState(
    val type: EventType = EventType.BIRTH,
    val eventDate: LocalDate = LocalDate.now(),
    val notes: String = "",
    val isSaving: Boolean = false,
    val saveError: String? = null,
)

@HiltViewModel
class EventFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val eventRepository: EventRepository,
) : ViewModel() {

    private val animalId: Long = savedStateHandle.get<Long>(Route.EventForm.ARG_ANIMAL) ?: -1L

    private val _state = MutableStateFlow(EventFormState())
    val state: StateFlow<EventFormState> = _state.asStateFlow()

    fun onTypeChange(type: EventType) = _state.update { it.copy(type = type) }
    fun onDateChange(date: LocalDate) = _state.update { it.copy(eventDate = date) }
    fun onNotesChange(value: String) = _state.update { it.copy(notes = value) }

    fun save(onSuccess: () -> Unit) {
        if (animalId <= 0) return
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
                    ),
                )
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(saveError = e.message ?: "Błąd zapisu", isSaving = false) }
            }
        }
    }
}
