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
 * Znacznik ostatniej udanej synchronizacji pobierającej.
 *
 * Wydzielone z implementacji na DataStore, żeby [SyncPuller] dało się przetestować
 * na zwykłej maszynie JVM, bez emulatora Androida.
 */
interface SyncMarkerStore {
    suspend fun lastPullMarker(): String?
    suspend fun saveLastPullMarker(marker: String)

    /**
     * Wiąże znacznik z konkretnym backendem. Gdy adres jest inny niż przy ostatnim
     * pobraniu, znacznik przepada — patrz [SyncStateRepository.bindToServer].
     */
    suspend fun bindToServer(baseUrl: String)
}

/** Czy telefon ma dane z serwera — stan pokazywany użytkownikowi. */
data class PullState(
    val lastPullAt: LocalDateTime? = null,
    val error: String? = null,
) {
    /** Nigdy nie udało się pobrać zmian: telefon pokazuje wyłącznie własne wpisy. */
    val neverPulled: Boolean get() = lastPullAt == null
}

/**
 * Pamięta znacznik ostatniej udanej synchronizacji pobierającej oraz jej wynik.
 *
 * Trzymamy tu dosłownie to, co przysłał backend w polu `server_time` — nie własny
 * czas urządzenia. Zegar telefonu bywa przestawiony, a przy porównywaniu dat
 * różnica choćby kilku minut albo gubi zmiany, albo każe pobierać je w kółko.
 *
 * Wynik pobierania zapisujemy osobno, bo bez niego nieudane pobranie było
 * niewidoczne: kolejka wysyłkowa świeciła „Zsynchronizowano”, choć z serwera nie
 * przyszło nic. Ekran stada czyta [observePullState] i mówi o tym wprost.
 */
@Singleton
class SyncStateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncMarkerStore {
    private val lastPullKey = stringPreferencesKey("last_pull_marker")
    private val serverKey = stringPreferencesKey("last_pull_server")
    private val pulledAtKey = stringPreferencesKey("last_pull_at")
    private val errorKey = stringPreferencesKey("last_pull_error")

    /** `null` przy pierwszym uruchomieniu — backend odpowie wtedy pełną migawką. */
    override suspend fun lastPullMarker(): String? =
        context.syncDataStore.data.map { it[lastPullKey] }.first()

    override suspend fun saveLastPullMarker(marker: String) {
        context.syncDataStore.edit { it[lastPullKey] = marker }
    }

    /**
     * Znacznik „pobrano do…” ma sens tylko wobec tego backendu, który go wystawił.
     * Po przesiadce na inny adres (inna sieć, serwer postawiony od nowa, świeża baza)
     * dalsze pytanie „co się zmieniło od 20 września” dostałoby pustą odpowiedź i
     * telefon utknąłby na własnych danych. Zmiana adresu zeruje więc znacznik, co
     * wymusza pełną migawkę i uzgodnienie stanu z serwerem.
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

    /** Wymusza pełne pobranie przy następnej synchronizacji (np. po zmianie serwera). */
    suspend fun reset() {
        context.syncDataStore.edit { it.remove(lastPullKey) }
    }

    private fun readPullState(prefs: Preferences) = PullState(
        lastPullAt = prefs[pulledAtKey]?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
        error = prefs[errorKey],
    )
}
