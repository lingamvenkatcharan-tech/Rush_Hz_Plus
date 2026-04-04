package com.example.rush_hz_plus.ui.alert

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarm_history")
data class AlarmItem(
    @PrimaryKey val id: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val soundLabel: String,
    val hazardLevel: String
)
