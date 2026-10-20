package com.example.herdmanagementmobile.ui.navigation

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
import java.time.LocalDate
import javax.inject.Inject

/**
 * Dostarcza dane dla nawigacji: licznik przy sekcji „Terminy”. Obejmuje on zdarzenia
 * po terminie, z terminem upływającym w ciągu dwóch dni oraz odrzucone przez rejestr.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    eventRepository: EventRepository,
) : ViewModel() {

    val dueCount: StateFlow<Int> = eventRepository.observeComplianceItems()
        .map { items ->
            val today = LocalDate.now()
            items.count {
                it.irzStatus == IrzStatus.REJECTED ||
                    it.urgency(today) != ComplianceItem.Urgency.ON_TIME
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
