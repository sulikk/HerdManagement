package com.example.herdmanagementmobile.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.herdmanagementmobile.data.settings.AppearanceRepository
import com.example.herdmanagementmobile.data.settings.AppearanceSettings
import com.example.herdmanagementmobile.data.settings.FarmSettingsRepository
import com.example.herdmanagementmobile.data.settings.ThemeMode
import com.example.herdmanagementmobile.domain.validation.HoldingNumber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val producerNumber: String = "",
    val holdingNumber: String = "",
    val producerError: String? = null,
    val holdingError: String? = null,
    val loaded: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: FarmSettingsRepository,
    private val appearanceRepository: AppearanceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    /** Zmiany wyglądu zapisują się od razu — nie ma tu czego „zatwierdzać”. */
    val appearance: StateFlow<AppearanceSettings> = appearanceRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceSettings())

    init {
        viewModelScope.launch {
            val s = repository.current()
            _state.update {
                it.copy(producerNumber = s.producerNumber, holdingNumber = s.holdingNumber, loaded = true)
            }
        }
    }

    fun onProducerChange(value: String) =
        _state.update { it.copy(producerNumber = value.trim(), producerError = null) }

    fun onHoldingChange(value: String) =
        _state.update { it.copy(holdingNumber = value.trim().uppercase(), holdingError = null) }

    fun onThemeMode(mode: ThemeMode) = viewModelScope.launch {
        appearanceRepository.setThemeMode(mode)
    }

    fun onHighContrast(enabled: Boolean) = viewModelScope.launch {
        appearanceRepository.setHighContrast(enabled)
    }

    fun onDynamicColor(enabled: Boolean) = viewModelScope.launch {
        appearanceRepository.setDynamicColor(enabled)
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        val producerError = if (!HoldingNumber.isValidProducer(s.producerNumber)) {
            "Numer producenta: dokładnie 9 cyfr"
        } else {
            null
        }
        val holdingError = if (!HoldingNumber.isValidHolding(s.holdingNumber)) {
            "Numer siedziby stada: PL + 9 cyfr (np. PL000111222)"
        } else {
            null
        }
        if (producerError != null || holdingError != null) {
            _state.update { it.copy(producerError = producerError, holdingError = holdingError) }
            return
        }
        viewModelScope.launch {
            repository.update(s.producerNumber, s.holdingNumber)
            onSaved()
        }
    }
}
