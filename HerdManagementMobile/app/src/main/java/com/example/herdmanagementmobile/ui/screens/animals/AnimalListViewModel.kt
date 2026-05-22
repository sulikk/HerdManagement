package com.example.herdmanagementmobile.ui.screens.animals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.repository.AnimalRepository
import com.example.herdmanagementmobile.domain.model.Animal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AnimalListViewModel @Inject constructor(
    repository: AnimalRepository,
) : ViewModel() {
    val animals: StateFlow<List<Animal>> = repository.observeAnimals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
