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
import com.example.herdmanagementmobile.domain.model.ListingStatus
import com.example.herdmanagementmobile.domain.model.Treatment
import com.example.herdmanagementmobile.domain.stats.AgeGroup
import com.example.herdmanagementmobile.domain.stats.HerdStatistics
import com.example.herdmanagementmobile.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class AnimalDetailState(
    val isLoading: Boolean = true,
    val animal: Animal? = null,
    val events: List<AnimalEvent> = emptyList(),
    val treatments: List<Treatment> = emptyList(),
    /** Identyfikator aktywnego ogłoszenia zwierzęcia (szkic lub opublikowane). */
    val activeListingId: Long? = null,
    /** Określa, czy zwierzę znajduje się w stadzie (nie ubyło i nie padło). */
    val inHerd: Boolean = true,
    /** Grupa wiekowo-płciowa zgodna z nazewnictwem statystyk. */
    val ageGroup: AgeGroup? = null,
    val ageMonths: Long? = null,
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
        listingRepository.observeAll(),
    ) { animal, events, treatments, listings ->
        val today = LocalDate.now()
        AnimalDetailState(
            isLoading = false,
            animal = animal,
            events = events,
            treatments = treatments,
            activeListingId = listings
                .firstOrNull {
                    it.animalId == animalId &&
                        (it.status == ListingStatus.DRAFT || it.status == ListingStatus.PUBLISHED)
                }
                ?.listingId,
            inHerd = HerdStatistics.isInHerd(events),
            ageGroup = animal?.let { HerdStatistics.ageGroup(it, today) },
            ageMonths = animal?.birthDate?.let { ChronoUnit.MONTHS.between(it, today) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AnimalDetailState(),
    )

    /** Zgłasza zdarzenie do IRZplus; wywoływane po potwierdzeniu w oknie dialogowym. */
    fun submitToIrz(eventId: Long) {
        viewModelScope.launch { eventRepository.submitToIrz(eventId) }
    }

    /** Usuwa zdarzenie niezapisane w rejestrze; dopuszczalność weryfikuje repozytorium. */
    fun deleteEvent(eventId: Long) {
        viewModelScope.launch { eventRepository.delete(eventId) }
    }

    fun delete(onDone: () -> Unit) {
        if (animalId <= 0) return
        viewModelScope.launch {
            animalRepository.delete(animalId)
            onDone()
        }
    }
}
