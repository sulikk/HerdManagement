package com.example.herdmanagementmobile.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
}

/**
 * Pamięta znacznik ostatniej udanej synchronizacji pobierającej.
 *
 * Trzymamy tu dosłownie to, co przysłał backend w polu `server_time` — nie własny
 * czas urządzenia. Zegar telefonu bywa przestawiony, a przy porównywaniu dat
 * różnica choćby kilku minut albo gubi zmiany, albo każe pobierać je w kółko.
 */
@Singleton
class SyncStateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncMarkerStore {
    private val lastPullKey = stringPreferencesKey("last_pull_marker")

    /** `null` przy pierwszym uruchomieniu — backend odpowie wtedy pełną migawką. */
    override suspend fun lastPullMarker(): String? =
        context.syncDataStore.data.map { it[lastPullKey] }.first()

    override suspend fun saveLastPullMarker(marker: String) {
        context.syncDataStore.edit { it[lastPullKey] = marker }
    }

    /** Wymusza pełne pobranie przy następnej synchronizacji (np. po zmianie serwera). */
    suspend fun reset() {
        context.syncDataStore.edit { it.remove(lastPullKey) }
    }
}
