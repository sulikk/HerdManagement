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
 * Dane potrzebne samej nawigacji — na razie licznik przy sekcji „Terminy”.
 *
 * Do licznika trafia to, co wymaga reakcji: zdarzenia po ustawowym terminie 7 dni,
 * te z terminem w ciągu dwóch dni oraz wszystkie odrzucone przez rejestr —
 * odrzucenie trzeba poprawić niezależnie od tego, ile czasu zostało.
 * Zdarzenia z zapasem czasu nie są liczone: gdyby plakietka świeciła się zawsze,
 * przestałaby cokolwiek znaczyć.
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
