package com.example.herdmanagementmobile.data.local.entity

import androidx.room.Entity

/**
 * Mapa lokalnych identyfikatorów Room na identyfikatory nadane przez backend.
 * Pozwala synchronizować rekordy zależne (np. zdarzenie wskazuje zwierzę po zdalnym id).
 */
@Entity(tableName = "id_mapping", primaryKeys = ["entity", "localId"])
data class IdMappingEntity(
    val entity: String,
    val localId: Long,
    val remoteId: Long,
)
