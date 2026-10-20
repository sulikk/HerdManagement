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

/** Stan synchronizacji w obu kierunkach: kolejka wysyłkowa ([pending], [failed]) i pobieranie ([pull]). */
data class SyncStatus(
    val pending: Int = 0,
    val failed: Int = 0,
    val pull: PullState = PullState(),
)

/**
 * Stan listy zwierząt po filtrowaniu.
 *
 * @property total liczebność całego stada, niezależnie od filtra.
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

    /** Stan kolejki wysyłkowej oraz wynik pobierania zmian z serwera. */
    val syncStatus: StateFlow<SyncStatus> = combine(
        syncManager.observePendingCount(),
        syncManager.observeFailedCount(),
        syncState.observePullState(),
    ) { pending, failed, pull -> SyncStatus(pending, failed, pull) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus())

    fun onQueryChange(value: String) = query.update { value }

    fun clearQuery() = query.update { "" }

    fun onSortSelected(field: AnimalSortField) = sort.update { it.select(field) }

    /** Wyzwala synchronizację ręczną, ponawiając również nieudane wpisy. */
    fun syncNow() {
        viewModelScope.launch { syncManager.syncNow() }
    }
}
