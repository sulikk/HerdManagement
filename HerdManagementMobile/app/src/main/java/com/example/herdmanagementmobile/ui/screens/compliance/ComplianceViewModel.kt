package com.example.herdmanagementmobile.ui.screens.compliance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.repository.EventRepository
import com.example.herdmanagementmobile.domain.model.ComplianceItem
import com.example.herdmanagementmobile.domain.model.IrzStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Stan ekranu terminów.
 *
 * @property waiting zdarzenia oczekujące na zgłoszenie.
 * @property rejected zgłoszenia odrzucone przez rejestr, wymagające poprawy danych.
 */
data class ComplianceState(
    val waiting: List<ComplianceItem> = emptyList(),
    val rejected: List<ComplianceItem> = emptyList(),
)

@HiltViewModel
class ComplianceViewModel @Inject constructor(
    private val repository: EventRepository,
) : ViewModel() {

    /** Pozycje posortowane według terminu, od najpilniejszych. */
    val state: StateFlow<ComplianceState> = repository.observeComplianceItems()
        .map { list ->
            val sorted = list.sortedBy { it.deadline }
            ComplianceState(
                waiting = sorted.filter { it.irzStatus != IrzStatus.REJECTED },
                rejected = sorted.filter { it.irzStatus == IrzStatus.REJECTED },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ComplianceState())

    /** Usuwa zgłoszenie niezapisane w rejestrze. */
    fun delete(eventId: Long) {
        viewModelScope.launch { repository.delete(eventId) }
    }

    /** Usuwa wszystkie zgłoszenia odrzucone przez rejestr. */
    fun deleteAllRejected() {
        val ids = state.value.rejected.map { it.eventId }
        viewModelScope.launch { ids.forEach { repository.delete(it) } }
    }
}
