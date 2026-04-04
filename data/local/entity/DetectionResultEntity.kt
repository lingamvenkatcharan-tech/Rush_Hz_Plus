// data/local/entity/DetectionResultEntity.kt
package com.example.rush_hz_plus.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.rush_hz_plus.domain.entity.DetectionResult
import com.example.rush_hz_plus.domain.score.HazardScore

@Entity(tableName = "detection_results")
data class DetectionResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 직접 FloatArray 사용 (TypeConverter로 자동 변환)
    val probabilities: FloatArray,

    val detectedClass: Int,
    val keywordDetected: Boolean,
    val snr: Float,
    val hazardScoreRawScore: Float,
    val hazardScoreNormalizedScore: Float,
    val hazardScoreLevel: String,
    val hazardScoreIsEmergency: Boolean,
    val timestamp: Long,
    val isSynced: Boolean,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val userId: String = "",
    val soundLabel: String = "",
    val alertStatus: String = "PENDING",        // PENDING, SHOWN, FAILED
    val alertType: String = "",                 // L1, L2, L3, EMERGENCY
    val alertTimestamp: Long = 0L,              // 실제 알림 발생 시간
    val alertMessage: String = "",              // 표시된 알림 메시지
    val guardianNotified: Boolean = false,      // 보호자 알림 여부 (L3 전용)
    val guardianNotificationType: String = ""   // SMS, APP, FAILED
) {
    companion object {
        fun fromDomain(domain: DetectionResult): DetectionResultEntity {
            return DetectionResultEntity(
                id = 0, // 항상 0 → Room이 자동 생성 보장
                probabilities = domain.probabilities,
                detectedClass = domain.detectedClass,
                keywordDetected = domain.keywordDetected,
                snr = domain.snr,
                hazardScoreRawScore = domain.hazardScore.rawScore,
                hazardScoreNormalizedScore = domain.hazardScore.normalizedScore,
                hazardScoreLevel = domain.hazardScore.level,
                hazardScoreIsEmergency = domain.hazardScore.isEmergency,
                timestamp = domain.timestamp,
                isSynced = domain.isSynced,
                latitude = domain.latitude,
                longitude = domain.longitude,
                userId = domain.userId,
                soundLabel = domain.soundLabel,
                alertStatus = domain.alertStatus,
                alertType = domain.alertType,
                alertTimestamp = domain.alertTimestamp,
                alertMessage = domain.alertMessage,
                guardianNotified = domain.guardianNotified,
                guardianNotificationType = domain.guardianNotificationType
            )
        }
    }

    // 단일 toDomain() 메서드만 유지
    fun toDomain(): DetectionResult {
        val hazardScore = HazardScore(
            rawScore = hazardScoreRawScore,
            normalizedScore = hazardScoreNormalizedScore,
            level = hazardScoreLevel,
            isEmergency = hazardScoreIsEmergency
        )
        return DetectionResult(
            id = id,
            probabilities = probabilities,
            detectedClass = detectedClass,
            keywordDetected = keywordDetected,
            snr = snr,
            hazardScore = hazardScore,
            timestamp = timestamp,
            isSynced = isSynced,
            latitude = latitude,
            longitude = longitude,
            userId = userId,
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