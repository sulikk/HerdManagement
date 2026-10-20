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

    @Insert
    suspend fun enqueue(entry: SyncQueueEntity): Long

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY id")
    suspend fun pending(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'FAILED' AND operation != 'IRZ_SUBMIT'")
    fun observeFailedCount(): Flow<Int>

    /**
     * Przywraca nieudane wpisy do kolejki. Zgłoszenia do IRZplus są pominięte, ponieważ
     * ich ponowienie wymaga ponownego potwierdzenia użytkownika.
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

    /**
     * Zapisuje mapowanie identyfikatora lokalnego na zdalny, zapewniając, że dany
     * identyfikator zdalny wskazuje dokładnie jeden rekord lokalny.
     *
     * Klucz główny `(entity, localId)` nie gwarantuje tej własności, a backend może
     * ponownie wykorzystać identyfikatory usuniętych wierszy.
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

    /** Zwraca identyfikator lokalny odpowiadający zdalnemu; używane przy pobieraniu zmian. */
    @Query("SELECT localId FROM id_mapping WHERE entity = :entity AND remoteId = :remoteId")
    suspend fun localIdFor(entity: String, remoteId: Long): Long?

    @Query("DELETE FROM id_mapping WHERE entity = :entity AND localId = :localId")
    suspend fun deleteMapping(entity: String, localId: Long)

    /**
     * Usuwa mapowania zdarzeń, leczeń, ogłoszeń i zapytań powiązanych ze zwierzęciem.
     *
     * Tabela `id_mapping` nie posiada kluczy obcych, dlatego nie podlega usuwaniu
     * kaskadowemu. Metodę należy wywołać przed usunięciem zwierzęcia. Mapowanie samego
     * zwierzęcia jest zachowywane, ponieważ jest potrzebne do wysłania usunięcia na serwer.
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

    /** Usuwa mapowania zapytań powiązanych z ogłoszeniem; wywoływane przed jego usunięciem. */
    @Query(
        "DELETE FROM id_mapping WHERE entity = 'inquiry' AND localId IN " +
            "(SELECT id FROM inquiries WHERE listingId = :listingId)",
    )
    suspend fun deleteInquiryMappingsForListing(listingId: Long)

    /** Zwraca wszystkie mapowania danej encji; używane przy uzgadnianiu pełnej migawki. */
    @Query("SELECT * FROM id_mapping WHERE entity = :entity")
    suspend fun mappingsFor(entity: String): List<IdMappingEntity>

    /**
     * Zwraca liczbę niewysłanych wpisów (oczekujących i nieudanych). Pobieranie zmian
     * z serwera rozpoczyna się dopiero przy wartości zero. Zgłoszenia do IRZplus nie są
     * wliczane, ponieważ ich ponowienie zależy od decyzji użytkownika.
     */
    @Query(
        "SELECT COUNT(*) FROM sync_queue " +
            "WHERE status IN ('PENDING', 'FAILED') AND operation != 'IRZ_SUBMIT'",
    )
    suspend fun unsentCount(): Int
}
