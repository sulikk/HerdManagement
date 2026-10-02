package com.example.herdmanagementmobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.herdmanagementmobile.data.local.entity.IdMappingEntity
import com.example.herdmanagementmobile.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {

    // --- Kolejka synchronizacji -------------------------------------

    @Insert
    suspend fun enqueue(entry: SyncQueueEntity): Long

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY id")
    suspend fun pending(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'FAILED' AND operation != 'IRZ_SUBMIT'")
    fun observeFailedCount(): Flow<Int>

    /**
     * Ręczne ponowienie nieudanych wpisów. Zgłoszenia do IRZ są pomijane — po porażce
     * wracają do stanu „Nie zgłoszono” i wymagają ponownego potwierdzenia użytkownika.
     */
    @Query(
        "UPDATE sync_queue SET status = 'PENDING', retries = 0, updatedAt = :updatedAt " +
            "WHERE status = 'FAILED' AND operation != 'IRZ_SUBMIT'",
    )
    suspend fun resetFailed(updatedAt: String): Int

    @Query(
        "UPDATE sync_queue SET status = :status, retries = :retries, " +
            "lastError = :error, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateEntry(
        id: Long,
        status: String,
        retries: Int,
        error: String?,
        updatedAt: String,
    )

    // --- Mapa identyfikatorów lokalne -> zdalne ----------------------

    /**
     * Zapisuje powiązanie lokalne ↔ zdalne, pilnując, że jedno zdalne id należy do
     * dokładnie jednego rekordu lokalnego.
     *
     * Klucz główny tabeli to `(entity, localId)`, więc sam z siebie nie broni przed
     * dwoma lokalnymi rekordami wskazującymi to samo zdalne id — a backend nadaje
     * numery usuniętych wierszy od nowa, więc nowy rekord dostaje numer po
     * poprzedniku. Zostawione stare powiązanie sprawiało, że [localIdFor] wskazywało
     * rekord już nieistniejący i pobieranie zmian wpisywało dane serwera pod jego
     * identyfikator — usunięte zdarzenie wracało z martwych, podpięte pod nowe zwierzę.
     */
    @Transaction
    suspend fun putMapping(mapping: IdMappingEntity) {
        clearRemoteId(mapping.entity, mapping.remoteId)
        insertMapping(mapping)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: IdMappingEntity)

    @Query("DELETE FROM id_mapping WHERE entity = :entity AND remoteId = :remoteId")
    suspend fun clearRemoteId(entity: String, remoteId: Long)

    @Query("SELECT remoteId FROM id_mapping WHERE entity = :entity AND localId = :localId")
    suspend fun remoteIdFor(entity: String, localId: Long): Long?

    /** Kierunek odwrotny — potrzebny przy pobieraniu zmian z backendu. */
    @Query("SELECT localId FROM id_mapping WHERE entity = :entity AND remoteId = :remoteId")
    suspend fun localIdFor(entity: String, remoteId: Long): Long?

    @Query("DELETE FROM id_mapping WHERE entity = :entity AND localId = :localId")
    suspend fun deleteMapping(entity: String, localId: Long)

    /**
     * Czyści powiązania zdarzeń, leczeń, ogłoszeń i zapytań danego zwierzęcia.
     *
     * Tabela `id_mapping` nie ma klucza obcego do żadnej z tabel danych (jedna tabela
     * opisuje pięć encji), więc kaskada przy usunięciu zwierzęcia jej nie dotyka i
     * powiązania dzieci zostają jako śmieci. W połączeniu z ponownym użyciem numerów
     * po stronie backendu takie śmieci przejmowały nowe rekordy.
     *
     * Wołać **przed** usunięciem zwierzęcia — po kaskadzie nie da się już ustalić,
     * które wiersze do niego należały. Powiązanie samego zwierzęcia zostaje: jest
     * jeszcze potrzebne, żeby wysłać usunięcie na serwer.
     */
    @Transaction
    suspend fun deleteChildMappingsForAnimal(animalId: Long) {
        deleteInquiryMappingsForAnimal(animalId)
        deleteListingMappingsForAnimal(animalId)
        deleteEventMappingsForAnimal(animalId)
        deleteTreatmentMappingsForAnimal(animalId)
    }

    @Query(
        "DELETE FROM id_mapping WHERE entity = 'event' AND localId IN " +
            "(SELECT id FROM events WHERE animalId = :animalId)",
    )
    suspend fun deleteEventMappingsForAnimal(animalId: Long)

    @Query(
        "DELETE FROM id_mapping WHERE entity = 'treatment' AND localId IN " +
            "(SELECT id FROM treatments WHERE animalId = :animalId)",
    )
    suspend fun deleteTreatmentMappingsForAnimal(animalId: Long)

    @Query(
        "DELETE FROM id_mapping WHERE entity = 'listing' AND localId IN " +
            "(SELECT id FROM listings WHERE animalId = :animalId)",
    )
    suspend fun deleteListingMappingsForAnimal(animalId: Long)

    @Query(
        "DELETE FROM id_mapping WHERE entity = 'inquiry' AND localId IN " +
            "(SELECT i.id FROM inquiries i JOIN listings l ON l.id = i.listingId " +
            "WHERE l.animalId = :animalId)",
    )
    suspend fun deleteInquiryMappingsForAnimal(animalId: Long)

    /**
     * To samo przy usunięciu samego ogłoszenia: kaskada zabiera jego zapytania,
     * a ich powiązania zostałyby w mapie jako śmieci. Wołać **przed** usunięciem.
     */
    @Query(
        "DELETE FROM id_mapping WHERE entity = 'inquiry' AND localId IN " +
            "(SELECT id FROM inquiries WHERE listingId = :listingId)",
    )
    suspend fun deleteInquiryMappingsForListing(listingId: Long)

    /**
     * Wszystkie znane powiązania dla encji.
     *
     * Potrzebne przy pełnej migawce: rekord, który ma mapowanie, ale nie ma już
     * odpowiednika na serwerze, został tam usunięty i musi zniknąć także lokalnie.
     * Rekordy bez mapowania są nietykalne — nigdy nie dotarły na serwer.
     */
    @Query("SELECT * FROM id_mapping WHERE entity = :entity")
    suspend fun mappingsFor(entity: String): List<IdMappingEntity>

    /**
     * Ile zmian czeka jeszcze na wysłanie (łącznie z nieudanymi).
     *
     * Pobieranie zmian z serwera ruszą dopiero, gdy to zejdzie do zera — inaczej
     * starsza wersja z backendu nadpisałaby lokalną poprawkę, której nie zdążyliśmy
     * wypchnąć. Zgłoszenia do IRZ pomijamy: po porażce wracają do ręcznej decyzji
     * użytkownika i mogłyby blokować synchronizację w nieskończoność.
     */
    @Query(
        "SELECT COUNT(*) FROM sync_queue " +
            "WHERE status IN ('PENDING', 'FAILED') AND operation != 'IRZ_SUBMIT'",
    )
    suspend fun unsentCount(): Int
}
