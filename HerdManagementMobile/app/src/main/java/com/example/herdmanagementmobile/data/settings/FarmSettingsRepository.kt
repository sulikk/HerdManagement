package com.example.herdmanagementmobile.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Ustawienia gospodarstwa współdzielone przez całą aplikację. */
data class FarmSettings(
    val producerNumber: String,
    val holdingNumber: String,
)

private val Context.farmDataStore by preferencesDataStore(name = "farm_settings")

/**
 * Przechowuje numer producenta i numer siedziby stada w DataStore.
 * Wartości używane jako domyślne dla zwierząt i jako poświadczenia/parametry
 * zgłoszeń do IRZplus. Wartości startowe są demonstracyjne (działają z mockiem).
 */
@Singleton
class FarmSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val producerKey = stringPreferencesKey("producer_number")
    private val holdingKey = stringPreferencesKey("holding_number")

    val settings: Flow<FarmSettings> = context.farmDataStore.data.map { prefs ->
        FarmSettings(
            producerNumber = prefs[producerKey] ?: DEFAULT_PRODUCER,
            holdingNumber = prefs[holdingKey] ?: DEFAULT_HOLDING,
        )
    }

    suspend fun current(): FarmSettings = settings.first()

    suspend fun update(producerNumber: String, holdingNumber: String) {
        context.farmDataStore.edit { prefs ->
            prefs[producerKey] = producerNumber
            prefs[holdingKey] = holdingNumber
        }
    }

    companion object {
        const val DEFAULT_PRODUCER = "123456789"
        const val DEFAULT_HOLDING = "PL000111222"
    }
}
