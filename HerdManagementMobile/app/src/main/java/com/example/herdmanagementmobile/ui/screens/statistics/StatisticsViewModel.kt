package com.example.herdmanagementmobile.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.repository.StatisticsRepository
import com.example.herdmanagementmobile.domain.stats.HerdStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    repository: StatisticsRepository,
) : ViewModel() {

    /** null = trwa pierwsze wczytanie. */
    val stats: StateFlow<HerdStats?> = repository.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
