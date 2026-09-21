package com.example.herdmanagementmobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.herdmanagementmobile.data.local.dao.AnimalDao
import com.example.herdmanagementmobile.data.local.dao.EventDao
import com.example.herdmanagementmobile.data.local.dao.InquiryDao
import com.example.herdmanagementmobile.data.local.dao.ListingDao
import com.example.herdmanagementmobile.data.local.dao.SyncDao
import com.example.herdmanagementmobile.data.local.dao.TreatmentDao
import com.example.herdmanagementmobile.data.local.entity.AnimalEntity
import com.example.herdmanagementmobile.data.local.entity.EventEntity
import com.example.herdmanagementmobile.data.local.entity.IdMappingEntity
import com.example.herdmanagementmobile.data.local.entity.InquiryEntity
import com.example.herdmanagementmobile.data.local.entity.ListingEntity
import com.example.herdmanagementmobile.data.local.entity.MeasurementEntity
import com.example.herdmanagementmobile.data.local.entity.SyncQueueEntity
import com.example.herdmanagementmobile.data.local.entity.TreatmentEntity

@Database(
    entities = [
        AnimalEntity::class,
        EventEntity::class,
        TreatmentEntity::class,
        MeasurementEntity::class,
        ListingEntity::class,
        SyncQueueEntity::class,
        IdMappingEntity::class,
        InquiryEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class HerdDatabase : RoomDatabase() {
    abstract fun animalDao(): AnimalDao
    abstract fun eventDao(): EventDao
    abstract fun treatmentDao(): TreatmentDao
    abstract fun syncDao(): SyncDao
    abstract fun listingDao(): ListingDao
    abstract fun inquiryDao(): InquiryDao

    companion object {
        const val DATABASE_NAME = "herd.db"
    }
}
