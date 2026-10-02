package com.example.herdmanagementmobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.herdmanagementmobile.data.local.entity.InquiryEntity
import com.example.herdmanagementmobile.data.local.entity.ListingEntity
import com.example.herdmanagementmobile.data.local.relation.ListingWithAnimal
import kotlinx.coroutines.flow.Flow

@Dao
interface ListingDao {

    /** Lista ogłoszeń wraz z danymi zwierzęcia — kolczyk i rasa są potrzebne na każdej pozycji. */
    @Query(
        "SELECT l.id AS listingId, l.animalId AS animalId, l.price AS price, " +
            "l.description AS description, l.status AS status, l.irzVerified AS irzVerified, " +
            "l.irzMessage AS irzMessage, l.createdAt AS createdAt, " +
            "a.earTag AS earTag, a.name AS animalName, a.breed AS breed, " +
            "(SELECT COUNT(*) FROM inquiries i WHERE i.listingId = l.id) AS inquiryCount " +
            "FROM listings l JOIN animals a ON a.id = l.animalId " +
            "ORDER BY l.createdAt DESC",
    )
    fun observeWithAnimal(): Flow<List<ListingWithAnimal>>

    @Query("SELECT * FROM listings WHERE id = :id")
    fun observeById(id: Long): Flow<ListingEntity?>

    @Query("SELECT * FROM listings WHERE id = :id")
    suspend fun getById(id: Long): ListingEntity?

    /** Ogłoszenie blokujące ponowne wystawienie zwierzęcia (szkic albo opublikowane). */
    @Query(
        "SELECT * FROM listings WHERE animalId = :animalId " +
            "AND status IN ('DRAFT', 'PUBLISHED') LIMIT 1",
    )
    suspend fun getActiveForAnimal(animalId: Long): ListingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(listing: ListingEntity): Long

    @Update
    suspend fun update(listing: ListingEntity)

    @Query("DELETE FROM listings WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface InquiryDao {

    @Query("SELECT * FROM inquiries WHERE listingId = :listingId ORDER BY createdAt DESC")
    fun observeForListing(listingId: Long): Flow<List<InquiryEntity>>

    /** Licznik do odznaki na liście ogłoszeń — hodowca widzi, że ktoś pyta. */
    @Query("SELECT COUNT(*) FROM inquiries")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(inquiry: InquiryEntity): Long

    @Query("SELECT * FROM inquiries WHERE id = :id")
    suspend fun getById(id: Long): InquiryEntity?

    @Query("DELETE FROM inquiries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
