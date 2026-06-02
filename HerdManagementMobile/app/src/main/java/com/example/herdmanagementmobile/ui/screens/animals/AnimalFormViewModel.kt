package com.example.herdmanagementmobile.ui.screens.animals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.repository.AnimalRepository
import com.example.herdmanagementmobile.data.settings.FarmSettingsRepository
import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.Sex
import com.example.herdmanagementmobile.domain.validation.EarTag
import com.example.herdmanagementmobile.domain.validation.HoldingNumber
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
    val birthDateError: String? = null,
    val herdIdError: String? = null,
    val saveError: String? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class AnimalFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AnimalRepository,
    private val farmSettings: FarmSettingsRepository,
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
        } else {
            // Domyślna siedziba stada z ustawień gospodarstwa (można nadpisać w polu).
            viewModelScope.launch {
                val default = farmSettings.current().holdingNumber
                _state.update { if (it.herdId.isBlank()) it.copy(herdId = default) else it }
            }
        }
    }

    fun onEarTagChange(value: String) =
        _state.update { it.copy(earTag = value.uppercase(), earTagError = null) }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onBirthDateChange(value: LocalDate?) =
        _state.update { it.copy(birthDate = value, birthDateError = null) }
    fun onBreedChange(value: String) = _state.update { it.copy(breed = value) }
    fun onSexChange(value: Sex) = _state.update { it.copy(sex = value) }
    fun onMotherEarTagChange(value: String) =
        _state.update { it.copy(motherEarTag = value.uppercase()) }

    fun onHerdIdChange(value: String) =
        _state.update { it.copy(herdId = value.uppercase(), herdIdError = null) }

    fun save(onSuccess: () -> Unit) {
        val s = _state.value
        if (!EarTag.isValidFormat(s.earTag)) {
            _state.update { it.copy(earTagError = "Format: PL + 12 cyfr (np. PL000123456789)") }
            return
        }
        if (s.birthDate == null) {
            _state.update { it.copy(birthDateError = "Data urodzenia jest wymagana.") }
            return
        }
        if (s.birthDate.isAfter(LocalDate.now())) {
            _state.update { it.copy(birthDateError = "Data urodzenia nie może być z przyszłości.") }
            return
        }
        if (s.motherEarTag.isNotBlank() && !EarTag.isValidFormat(s.motherEarTag)) {
            _state.update { it.copy(saveError = "Kolczyk matki ma zły format (PL + 12 cyfr).") }
            return
        }
        if (s.herdId.isNotBlank() && !HoldingNumber.isValidHolding(s.herdId)) {
            _state.update { it.copy(herdIdError = "Format: PL + 9 cyfr (np. PL000111222)") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveError = null, earTagError = null) }

            // Walidacja unikalności kolczyka — czytelny komunikat zamiast wyjątku z bazy.
            val existing = repository.getByEarTag(s.earTag)
            if (existing != null && existing.id != animalId) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        earTagError = "Zwierzę o tym kolczyku już istnieje w stadzie.",
                    )
                }
                return@launch
            }

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
                _state.update {
                    it.copy(
                        saveError = "Nie udało się zapisać zwierzęcia. Spróbuj ponownie.",
                        isSaving = false,
                    )
                }
            }
        }
    }
}
