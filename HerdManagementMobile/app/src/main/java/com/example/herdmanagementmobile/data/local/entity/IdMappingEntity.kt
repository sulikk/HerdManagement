package com.example.herdmanagementmobile.data.local.entity

import androidx.room.Entity

/**
 * Mapowanie identyfikatorów lokalnych na identyfikatory nadane przez backend.
 * Umożliwia synchronizację rekordów zależnych, które odwołują się do rekordów nadrzędnych.
 */
@Entity(tableName = "id_mapping", primaryKeys = ["entity", "localId"])
data class IdMappingEntity(
    val entity: String,
    val localId: Long,
    val remoteId: Long,
)
