package com.example.rush_hz_plus.data.model

data class GuardianNotificationStatus(
    val detectionId: Long,
    val guardianId: String,
    val notified: Boolean,
    val type: String,  // e.g., "SMS_DELIVERED", "SMS_TIMEOUT", "APP"
    val timestamp: Long
)