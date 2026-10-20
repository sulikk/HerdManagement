package com.example.herdmanagementmobile.data.sync

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore by preferencesDataStore(name = "sync_state")

/**
 * Magazyn znacznika ostatniego udanego pobrania zmian.
 *
 * Abstrakcja umożliwia testowanie [SyncPuller] na JVM, bez zależności od DataStore.
 */
interface SyncMarkerStore {
    suspend fun lastPullMarker(): String?
    suspend fun saveLastPullMarker(marker: String)

    /**
     * Wiąże znacznik z adresem backendu; zmiana adresu unieważnia znacznik
     * (zob. [SyncStateRepository.bindToServer]).
     */
    suspend fun bindToServer(baseUrl: String)
}

/** Stan pobierania zmian z serwera prezentowany użytkownikowi. */
data class PullState(
    val lastPullAt: LocalDateTime? = null,
    val error: String? = null,
) {
    /** Określa, czy żadne pobranie zmian nie zakończyło się dotąd sukcesem. */
    val neverPulled: Boolean get() = lastPullAt == null
}

/**
 * Przechowuje znacznik ostatniego udanego pobrania zmian oraz wynik ostatniej próby.
 *
 * Znacznikiem jest wartość `server_time` zwrócona przez backend, a nie czas urządzenia,
 * dzięki czemu rozbieżność zegarów nie powoduje utraty ani wielokrotnego pobierania zmian.
 * Wynik ostatniej próby jest udostępniany przez [observePullState].
 */
@Singleton
class SyncStateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncMarkerStore {
    private val lastPullKey = stringPreferencesKey("last_pull_marker")
    private val serverKey = stringPreferencesKey("last_pull_server")
    private val pulledAtKey = stringPreferencesKey("last_pull_at")
    private val errorKey = stringPreferencesKey("last_pull_error")

    /** Zwraca `null` przy pierwszym uruchomieniu, co skutkuje pobraniem pełnej migawki. */
    override suspend fun lastPullMarker(): String? =
        context.syncDataStore.data.map { it[lastPullKey] }.first()

    override suspend fun saveLastPullMarker(marker: String) {
        context.syncDataStore.edit { it[lastPullKey] = marker }
    }

    /**
     * Znacznik jest ważny wyłącznie dla serwera, który go wystawił. Zmiana adresu
     * backendu usuwa znacznik, wymuszając pobranie pełnej migawki i uzgodnienie stanu.
     */
    override suspend fun bindToServer(baseUrl: String) {
        context.syncDataStore.edit { prefs ->
            if (prefs[serverKey] != baseUrl) {
                prefs.remove(lastPullKey)
                prefs[serverKey] = baseUrl
            }
        }
    }

    fun observePullState(): Flow<PullState> = context.syncDataStore.data.map(::readPullState)

    suspend fun recordPullSuccess() {
        context.syncDataStore.edit {
            it[pulledAtKey] = LocalDateTime.now().toString()
            it.remove(errorKey)
        }
    }

    suspend fun recordPullFailure(message: String) {
        context.syncDataStore.edit { it[errorKey] = message }
    }

    /** Wymusza pobranie pełnej migawki przy następnej synchronizacji. */
    suspend fun reset() {
        context.syncDataStore.edit { it.remove(lastPullKey) }
    }

    private fun readPullState(prefs: Preferences) = PullState(
        lastPullAt = prefs[pulledAtKey]?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
        error = prefs[errorKey],
    )
}
