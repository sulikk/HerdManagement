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
 * Terminy podzielone wg tego, czego wymagają od hodowcy.
 *
 * [waiting] to zdarzenia, które wystarczy wysłać. [rejected] odrzucił rejestr —
 * tam samo ponowienie nic nie da, trzeba najpierw poprawić dane. Mieszanie ich
 * na jednej liście zaśmiecało widok terminów pozycjami, których nie da się
 * załatwić jednym kliknięciem.
 */
data class ComplianceState(
    val waiting: List<ComplianceItem> = emptyList(),
    val rejected: List<ComplianceItem> = emptyList(),
)

@HiltViewModel
class ComplianceViewModel @Inject constructor(
    private val repository: EventRepository,
) : ViewModel() {

    /** Pozycje posortowane wg terminu (najpilniejsze na górze). */
    val state: StateFlow<ComplianceState> = repository.observeComplianceItems()
        .map { list ->
            val sorted = list.sortedBy { it.deadline }
            ComplianceState(
                waiting = sorted.filter { it.irzStatus != IrzStatus.REJECTED },
                rejected = sorted.filter { it.irzStatus == IrzStatus.REJECTED },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ComplianceState())

    /**
     * Usuwa zgłoszenie, którego nie da się naprawić — na przykład urodzenie sztuki, która
     * jest w rejestrze od dawna. Bez tego lista „do poprawy” rosłaby bez końca pozycjami,
     * z którymi nie ma co zrobić, i przestałaby pokazywać to, co faktycznie wymaga uwagi.
     */
    fun delete(eventId: Long) {
        viewModelScope.launch { repository.delete(eventId) }
    }

    /** Hurtowe sprzątnięcie odrzuconych — przy kilkudziesięciu sztukach pozycja po pozycji. */
    fun deleteAllRejected() {
        val ids = state.value.rejected.map { it.eventId }
        viewModelScope.launch { ids.forEach { repository.delete(it) } }
    }
}
