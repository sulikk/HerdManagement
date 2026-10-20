package com.example.herdmanagementmobile.ui.screens.listings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.repository.AnimalRepository
import com.example.herdmanagementmobile.data.repository.ListingRepository
import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.Inquiry
import com.example.herdmanagementmobile.domain.model.Listing
import com.example.herdmanagementmobile.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ogłoszenie wraz z danymi zwierzęcia, którego dotyczy. */
data class ListingDetail(
    val listing: Listing,
    val animal: Animal?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListingDetailViewModel @Inject constructor(
    private val repository: ListingRepository,
    animalRepository: AnimalRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val listingId: Long = checkNotNull(savedStateHandle[Route.ListingDetail.ARG])

    val detail: StateFlow<ListingDetail?> = repository.observeById(listingId)
        .flatMapLatest { listing ->
            if (listing == null) {
                flowOf(null)
            } else {
                animalRepository.observeById(listing.animalId)
                    .combine(flowOf(listing)) { animal, current -> ListingDetail(current, animal) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val inquiries: StateFlow<List<Inquiry>> = repository.observeInquiries(listingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun dismissMessage() = _message.update { null }

    /** Publikuje ogłoszenie po potwierdzeniu w oknie dialogowym (wymaganie F12). */
    fun publish() = viewModelScope.launch {
        repository.publish(listingId)
        _message.update { "Ogłoszenie wysłane do publikacji." }
    }

    fun withdraw() = viewModelScope.launch {
        repository.withdraw(listingId)
        _message.update { "Ogłoszenie wycofane." }
    }

    fun markSold() = viewModelScope.launch {
        repository.markSold(listingId)
        _message.update {
            "Oznaczono jako sprzedane. Pamiętaj o zgłoszeniu ubycia do IRZplus w ciągu 7 dni."
        }
    }

    /** Zleca weryfikację danych w rejestrze IRZplus (wymaganie F14); wynik jest pobierany synchronizacją. */
    fun verify() = viewModelScope.launch {
        repository.requestVerification(listingId)
        _message.update { "Wysłano do sprawdzenia w IRZplus. Odznaka pojawi się po odpowiedzi rejestru." }
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        repository.delete(listingId)
        onDeleted()
    }
}
