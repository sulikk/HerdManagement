package com.example.herdmanagementmobile.ui.screens.listings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.repository.ListingRepository
import com.example.herdmanagementmobile.domain.model.Listing
import com.example.herdmanagementmobile.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListingFormState(
    val earTag: String = "",
    val animalName: String? = null,
    val breed: String? = null,
    val price: String = "",
    val description: String = "",
    val priceError: String? = null,
    val editing: Boolean = false,
    val saving: Boolean = false,
    val blockedByExisting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ListingFormViewModel @Inject constructor(
    private val repository: ListingRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val animalId: Long = checkNotNull(savedStateHandle[Route.ListingForm.ARG_ANIMAL])
    private val listingId: Long? = savedStateHandle.get<Long>(Route.ListingForm.ARG_LISTING)
        ?.takeIf { it > 0 }

    private val _state = MutableStateFlow(ListingFormState(editing = listingId != null))
    val state: StateFlow<ListingFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch { prefill() }
    }

    /**
     * Wymaganie F13 — pola startują z danymi karty zwierzęcia. Dane bierzemy
     * z lokalnej bazy, więc formularz działa też bez zasięgu.
     */
    private suspend fun prefill() {
        val draft = repository.draftFor(animalId)
        if (draft == null) {
            _state.update { it.copy(error = "Nie znaleziono zwierzęcia.") }
            return
        }

        val existing = listingId?.let { repository.activeListingFor(animalId) }
        _state.update {
            it.copy(
                earTag = draft.earTag,
                animalName = draft.name,
                breed = draft.breed,
                description = existing?.description ?: draft.suggestedDescription,
                price = existing?.price?.let(::formatPlainPrice).orEmpty(),
                // Blokada dotyczy tylko zakładania nowego: backend i tak odrzuci
                // drugie aktywne ogłoszenie tej samej sztuki.
                blockedByExisting = listingId == null && draft.existingListingId != null,
            )
        }
    }

    fun onPriceChange(value: String) =
        _state.update { it.copy(price = value, priceError = null) }

    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }

    fun dismissError() = _state.update { it.copy(error = null) }

    /**
     * Zapisuje ogłoszenie jako **szkic**. Publikacja to osobna, jawna decyzja
     * podejmowana na karcie ogłoszenia (F12).
     */
    fun save(onSaved: (Long) -> Unit) {
        val current = _state.value
        val price = current.price.replace(',', '.').trim().toDoubleOrNull()
        if (price == null || price <= 0) {
            _state.update { it.copy(priceError = "Podaj cenę większą od zera.") }
            return
        }

        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                if (listingId != null) {
                    val existing = repository.activeListingFor(animalId)
                        ?: error("Ogłoszenie zniknęło z bazy.")
                    repository.update(
                        existing.copy(price = price, description = current.description.ifBlank { null }),
                    )
                    listingId
                } else {
                    repository.create(
                        Listing(
                            animalId = animalId,
                            price = price,
                            description = current.description.ifBlank { null },
                        ),
                    )
                }
            }.onSuccess { id ->
                _state.update { it.copy(saving = false) }
                onSaved(id)
            }.onFailure { error ->
                _state.update {
                    it.copy(saving = false, error = error.message ?: "Nie udało się zapisać ogłoszenia.")
                }
            }
        }
    }

    private fun formatPlainPrice(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
