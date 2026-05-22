package com.example.herdmanagementmobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val entity: String,
    val operation: String,
    val payload: String,
    val status: String,
    val retries: Int = 0,
    val lastError: String? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
