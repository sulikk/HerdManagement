package com.example.herdmanagementmobile.ui.screens.animals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.repository.AnimalRepository
import com.example.herdmanagementmobile.data.repository.EventRepository
import com.example.herdmanagementmobile.data.repository.ListingRepository
import com.example.herdmanagementmobile.data.repository.TreatmentRepository
import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.Treatment
import com.example.herdmanagementmobile.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnimalDetailState(
    val isLoading: Boolean = true,
    val animal: Animal? = null,
    val events: List<AnimalEvent> = emptyList(),
    val treatments: List<Treatment> = emptyList(),
    /** Ogloszenie zajmujace te sztuke - szkic albo opublikowane (zadanie 9). */
    val activeListingId: Long? = null,
)

@HiltViewModel
class AnimalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val animalRepository: AnimalRepository,
    private val eventRepository: EventRepository,
    treatmentRepository: TreatmentRepository,
    private val listingRepository: ListingRepository,
) : ViewModel() {

    val animalId: Long = savedStateHandle.get<Long>(Route.AnimalDetail.ARG) ?: -1L

    val state: StateFlow<AnimalDetailState> = combine(
        animalRepository.observeById(animalId),
        eventRepository.observeForAnimal(animalId),
        treatmentRepository.observeForAnimal(animalId),
    ) { animal, events, treatments ->
        AnimalDetailState(
            isLoading = false,
            animal = animal,
            events = events,
            treatments = treatments,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AnimalDetailState(),
    )

    /** Wywoływane po potwierdzeniu w dialogu — zgłasza zdarzenie do IRZplus. */
    fun submitToIrz(eventId: Long) {
        viewModelScope.launch { eventRepository.submitToIrz(eventId) }
    }

    /**
     * Sprawdza, czy zwierze ma juz aktywne ogloszenie. Decyduje, czy przycisk
     * prowadzi do formularza, czy do istniejacej karty ogloszenia.
     */
    suspend fun activeListingId(): Long? =
        if (animalId <= 0) null else listingRepository.activeListingFor(animalId)?.id

    fun delete(onDone: () -> Unit) {
        if (animalId <= 0) return
        viewModelScope.launch {
            animalRepository.delete(animalId)
            onDone()
        }
    }
}
