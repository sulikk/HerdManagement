package com.example.herdmanagementmobile.ui.screens.listings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.local.relation.ListingWithAnimal
import com.example.herdmanagementmobile.data.repository.ListingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ListingListState(
    val filter: ListingFilter = ListingFilter(),
    /** Pozycje każdej zakładki po zastosowaniu filtrów. */
    val visible: Map<ListingGroup, List<ListingWithAnimal>> = emptyMap(),
    /** Liczebność zakładek przed filtrowaniem. */
    val totals: Map<ListingGroup, Int> = emptyMap(),
) {
    val isEmpty: Boolean get() = totals.values.sum() == 0
    val filtering: Boolean get() = filter.query.isNotBlank() || filter.verifiedOnly

    fun visibleIn(group: ListingGroup): List<ListingWithAnimal> = visible[group].orEmpty()
    fun totalIn(group: ListingGroup): Int = totals[group] ?: 0
}

@HiltViewModel
class ListingListViewModel @Inject constructor(
    repository: ListingRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(ListingFilter())

    val state: StateFlow<ListingListState> = combine(
        repository.observeAll(),
        filter,
    ) { all, current ->
        val byGroup = ListingGroup.entries.associateWith { group ->
            all.filter { group.accepts(it.status) }
        }
        ListingListState(
            filter = current,
            visible = byGroup.mapValues { (_, items) -> items.applyFilter(current) },
            totals = byGroup.mapValues { (_, items) -> items.size },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListingListState())

    fun onQueryChange(value: String) = filter.update { it.copy(query = value) }

    fun clearQuery() = filter.update { it.copy(query = "") }

    fun onSortSelected(field: ListingSortField) =
        filter.update { it.copy(sort = it.sort.select(field)) }

    fun toggleVerifiedOnly() = filter.update { it.copy(verifiedOnly = !it.verifiedOnly) }
}
