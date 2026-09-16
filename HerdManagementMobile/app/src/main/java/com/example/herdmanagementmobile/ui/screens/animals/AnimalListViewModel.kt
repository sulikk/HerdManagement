package com.example.herdmanagementmobile.ui.screens.animals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.repository.AnimalRepository
import com.example.herdmanagementmobile.data.sync.SyncManager
import com.example.herdmanagementmobile.domain.model.Animal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Stan kolejki synchronizacji widoczny na liście zwierząt. */
data class SyncStatus(val pending: Int = 0, val failed: Int = 0)

@HiltViewModel
class AnimalListViewModel @Inject constructor(
    repository: AnimalRepository,
    private val syncManager: SyncManager,
) : ViewModel() {
    val animals: StateFlow<List<Animal>> = repository.observeAnimals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Operacje czekające na wysłanie i te, które się nie powiodły. */
    val syncStatus: StateFlow<SyncStatus> = combine(
        syncManager.observePendingCount(),
        syncManager.observeFailedCount(),
    ) { pending, failed -> SyncStatus(pending, failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus())

    /** Ręczna synchronizacja: ponawia nieudane wpisy i wysyła kolejkę od razu. */
    fun syncNow() {
        viewModelScope.launch { syncManager.syncNow() }
    }
}
