package com.example.rush_hz_plus.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "guardian_notifications",
    primaryKeys = ["detectionId", "guardianId"],
    foreignKeys = [
        ForeignKey(
            entity = DetectionResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["detectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GuardianNotificationEntity(
    val detectionId: Long,
    val guardianId: String,      // UID 또는 정규화된 전화번호
    val notified: Boolean,
    val type: String,            // "APP" or "SMS"
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)