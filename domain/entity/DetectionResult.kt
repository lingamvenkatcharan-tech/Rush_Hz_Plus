// domain/entity/DetectionResult.kt
package com.example.rush_hz_plus.domain.entity

import com.example.rush_hz_plus.domain.score.HazardScore


data class DetectionResult(
    val id: Long = 0,
    val probabilities: FloatArray,
    val detectedClass: Int,
    val keywordDetected: Boolean,
    val snr: Float,
    val hazardScore: HazardScore,
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val userId: String = "",
    val soundLabel: String = "", // 사전 계산된 소리 라벨
    val alertStatus: String = "PENDING",
    val alertType: String = "",
    val alertTimestamp: Long = 0L,
    val alertMessage: String = "",
    val guardianNotified: Boolean = false,
    val guardianNotificationType: String = ""
) {
    companion object {
        fun from(
            probabilities: FloatArray,
            detectedClass: Int,
            keywordDetected: Boolean,
            snr: Float,
            hazardScore: HazardScore,
            latitude: Double = 0.0,      // 위치 추가
            longitude: Double = 0.0,     // 위치 추가
            userId: String = "",         // 사용자 ID 추가
            timestamp: Long = System.currentTimeMillis(),
            isSynced: Boolean = false,
            soundLabel: String,
            alertStatus: String = "PENDING",
            alertType: String = "",
            alertTimestamp: Long = 0L,
            alertMessage: String = "",
            guardianNotified: Boolean = false,
            guardianNotificationType: String = ""
        ): DetectionResult {
            return DetectionResult(
                id = 0,
                probabilities = probabilities,
                detectedClass = detectedClass,
                keywordDetected = keywordDetected,
                snr = snr,
                hazardScore = hazardScore,
                latitude = latitude,
                longitude = longitude,
                userId = userId,
                timestamp = timestamp,
                isSynced = isSynced,
                soundLabel = soundLabel,
                alertStatus = alertStatus,
                alertType = alertType,
                alertTimestamp = alertTimestamp,
                alertMessage = alertMessage,
                guardianNotified = guardianNotified,
                guardianNotificationType = guardianNotificationType
            )
        }
    }
}