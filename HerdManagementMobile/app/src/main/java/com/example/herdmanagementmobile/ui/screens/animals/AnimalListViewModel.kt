package com.example.herdmanagementmobile.ui.screens.animals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.repository.AnimalRepository
import com.example.herdmanagementmobile.data.sync.PullState
import com.example.herdmanagementmobile.data.sync.SyncManager
import com.example.herdmanagementmobile.data.sync.SyncStateRepository
import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.search.AnimalSort
import com.example.herdmanagementmobile.domain.search.AnimalSortField
import com.example.herdmanagementmobile.domain.search.searchAndSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Stan synchronizacji widoczny na liście zwierząt — w obie strony.
 *
 * Sama kolejka wysyłkowa ([pending], [failed]) nie wystarczy: można mieć wszystko
 * wysłane i jednocześnie nie mieć ani jednego rekordu z serwera. Taki telefon
 * pokazywał „Zsynchronizowano”, choć stado na ekranie i w ewidencji było różne,
 * więc stan pobierania ([pull]) jest tu równoprawny.
 */
data class SyncStatus(
    val pending: Int = 0,
    val failed: Int = 0,
    val pull: PullState = PullState(),
)

/**
 * Widok stada: wynik filtrowania wraz z licznikami.
 *
 * [total] to liczebność całego stada — bez niej hodowca nie wie, czy pusta lista
 * oznacza brak zwierząt, czy zbyt wąskie wyszukiwanie.
 */
data class AnimalListState(
    val animals: List<Animal> = emptyList(),
    val query: String = "",
    val sort: AnimalSort = AnimalSort(),
    val total: Int = 0,
) {
    val filtering: Boolean get() = query.isNotBlank()
}

@HiltViewModel
class AnimalListViewModel @Inject constructor(
    repository: AnimalRepository,
    private val syncManager: SyncManager,
    syncState: SyncStateRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(AnimalSort())

    val state: StateFlow<AnimalListState> = combine(
        repository.observeAnimals(),
        query,
        sort,
    ) { animals, text, order ->
        AnimalListState(
            animals = animals.searchAndSort(text, order),
            query = text,
            sort = order,
            total = animals.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnimalListState())

    /** Operacje czekające na wysłanie, te nieudane oraz wynik pobierania z serwera. */
    val syncStatus: StateFlow<SyncStatus> = combine(
        syncManager.observePendingCount(),
        syncManager.observeFailedCount(),
        syncState.observePullState(),
    ) { pending, failed, pull -> SyncStatus(pending, failed, pull) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus())

    fun onQueryChange(value: String) = query.update { value }

    fun clearQuery() = query.update { "" }

    /** Ten sam wybór odwraca kierunek, inny — przełącza kryterium. */
    fun onSortSelected(field: AnimalSortField) = sort.update { it.select(field) }

    /** Ręczna synchronizacja: ponawia nieudane wpisy i wysyła kolejkę od razu. */
    fun syncNow() {
        viewModelScope.launch { syncManager.syncNow() }
    }
}
