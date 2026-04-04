// data/repository/DetectionRepositoryInterface.kt
package com.example.rush_hz_plus.data.repository

import com.example.rush_hz_plus.data.local.entity.DetectionResultEntity
import com.example.rush_hz_plus.data.model.GuardianNotificationStatus
import com.example.rush_hz_plus.domain.entity.DetectionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface DetectionRepositoryInterface {
    val emergencyEvent: SharedFlow<Unit>
    fun getAllDetections(): Flow<List<DetectionResult>>
    fun getUnsyncedCount(): Flow<Int>
    fun getLatestDetection(): Flow<DetectionResult?>
    suspend fun syncToRtdb(entity: DetectionResultEntity)
    suspend fun syncPendingDetections()
    // 실시간 감지 결과 제출
    suspend fun submitDetectionResult(result: DetectionResult): Long
    // 실시간 감지 결과 관찰 (UI용)
    val latestRealtimeDetection: StateFlow<DetectionResult?>
    // 알림 상태 관련 메서드 추가
    suspend fun updateAlertStatus(id: Long, status: String, type: String, timestamp: Long, message: String)
    suspend fun updateGuardianNotification(id: Long, guardianId: String, notified: Boolean, type: String)
    suspend fun getGuardianNotificationStatus(detectionId: Long, guardianId: String): GuardianNotificationStatus?
    fun getAlertHistory(): Flow<List<DetectionResult>>
    suspend fun getPendingAlerts(): List<DetectionResult>
}