package com.example.herdmanagementmobile.ui.screens.treatments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.repository.TreatmentRepository
import com.example.herdmanagementmobile.domain.model.Treatment
import com.example.herdmanagementmobile.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TreatmentFormState(
    val drug: String = "",
    val dose: String = "",
    val vet: String = "",
    val treatmentDate: LocalDate = LocalDate.now(),
    val withdrawalUntil: LocalDate? = null,
    val notes: String = "",
    val isSaving: Boolean = false,
    val drugError: String? = null,
    val saveError: String? = null,
)

@HiltViewModel
class TreatmentFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val treatmentRepository: TreatmentRepository,
) : ViewModel() {

    private val animalId: Long = savedStateHandle.get<Long>(Route.TreatmentForm.ARG_ANIMAL) ?: -1L

    private val _state = MutableStateFlow(TreatmentFormState())
    val state: StateFlow<TreatmentFormState> = _state.asStateFlow()

    fun onDrugChange(value: String) = _state.update { it.copy(drug = value, drugError = null) }
    fun onDoseChange(value: String) = _state.update { it.copy(dose = value) }
    fun onVetChange(value: String) = _state.update { it.copy(vet = value) }
    fun onDateChange(value: LocalDate) = _state.update { it.copy(treatmentDate = value) }
    fun onWithdrawalChange(value: LocalDate?) = _state.update { it.copy(withdrawalUntil = value) }
    fun onNotesChange(value: String) = _state.update { it.copy(notes = value) }

    fun save(onSuccess: () -> Unit) {
        if (animalId <= 0) return
        val s = _state.value
        if (s.drug.isBlank()) {
            _state.update { it.copy(drugError = "Podaj nazwę leku") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveError = null) }
            try {
                treatmentRepository.add(
                    Treatment(
                        animalId = animalId,
                        drug = s.drug.trim(),
                        dose = s.dose.ifBlank { null },
                        vet = s.vet.ifBlank { null },
                        treatmentDate = s.treatmentDate,
                        withdrawalUntil = s.withdrawalUntil,
                        notes = s.notes.ifBlank { null },
                    ),
                )
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(saveError = e.message ?: "Błąd zapisu", isSaving = false) }
            }
        }
    }
}
