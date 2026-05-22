package com.example.herdmanagementmobile.ui.screens.animals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.repository.AnimalRepository
import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.Sex
import com.example.herdmanagementmobile.domain.validation.EarTag
import com.example.herdmanagementmobile.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AnimalFormState(
    val earTag: String = "",
    val name: String = "",
    val birthDate: LocalDate? = null,
    val breed: String = "",
    val sex: Sex = Sex.FEMALE,
    val motherEarTag: String = "",
    val herdId: String = "",
    val earTagError: String? = null,
    val saveError: String? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class AnimalFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AnimalRepository,
) : ViewModel() {

    private val animalId: Long = savedStateHandle.get<Long>(Route.AnimalForm.ARG) ?: -1L
    val isEdit: Boolean = animalId > 0

    private val _state = MutableStateFlow(AnimalFormState())
    val state: StateFlow<AnimalFormState> = _state.asStateFlow()

    init {
        if (isEdit) {
            viewModelScope.launch {
                repository.getById(animalId)?.let { animal ->
                    _state.update {
                        it.copy(
                            earTag = animal.earTag,
                            name = animal.name.orEmpty(),
                            birthDate = animal.birthDate,
                            breed = animal.breed.orEmpty(),
                            sex = animal.sex,
                            motherEarTag = animal.motherEarTag.orEmpty(),
                            herdId = animal.herdId.orEmpty(),
                        )
                    }
                }
            }
        }
    }

    fun onEarTagChange(value: String) =
        _state.update { it.copy(earTag = value.uppercase(), earTagError = null) }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onBirthDateChange(value: LocalDate?) = _state.update { it.copy(birthDate = value) }
    fun onBreedChange(value: String) = _state.update { it.copy(breed = value) }
    fun onSexChange(value: Sex) = _state.update { it.copy(sex = value) }
    fun onMotherEarTagChange(value: String) =
        _state.update { it.copy(motherEarTag = value.uppercase()) }

    fun onHerdIdChange(value: String) = _state.update { it.copy(herdId = value) }

    fun save(onSuccess: () -> Unit) {
        val s = _state.value
        if (!EarTag.isValidFormat(s.earTag)) {
            _state.update { it.copy(earTagError = "Format: PL + 12 cyfr (np. PL000123456789)") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveError = null) }
            try {
                val animal = Animal(
                    id = if (isEdit) animalId else 0L,
                    earTag = s.earTag,
                    name = s.name.ifBlank { null },
                    birthDate = s.birthDate,
                    breed = s.breed.ifBlank { null },
                    sex = s.sex,
                    motherEarTag = s.motherEarTag.ifBlank { null },
                    herdId = s.herdId.ifBlank { null },
                )
                if (isEdit) repository.update(animal) else repository.add(animal)
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(saveError = e.message ?: "Błąd zapisu", isSaving = false) }
            }
        }
    }
}
