package com.example.herdmanagementmobile.ui.screens.compliance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.repository.EventRepository
import com.example.herdmanagementmobile.domain.model.ComplianceItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ComplianceViewModel @Inject constructor(
    repository: EventRepository,
) : ViewModel() {

    /** Pozycje posortowane wg terminu (najpilniejsze na górze). */
    val items: StateFlow<List<ComplianceItem>> = repository.observeComplianceItems()
        .map { list -> list.sortedBy { it.deadline } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
