// data/local/dao/DetectionDao.kt
package com.example.rush_hz_plus.data.local.dao

import androidx.room.*
import com.example.rush_hz_plus.data.local.entity.DetectionResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionDao {
    @Insert
    suspend fun insert(entity: DetectionResultEntity): Long

    @Query("SELECT COUNT(*) FROM detection_results WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>

    @Query("SELECT * FROM detection_results ORDER BY timestamp DESC")
    fun getAllResults(): Flow<List<DetectionResultEntity>>

    @Query("SELECT * FROM detection_results WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedResults(): List<DetectionResultEntity>

    @Query("UPDATE detection_results SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("UPDATE detection_results SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSyncedById(id: Long) // suspend 통일

    @Query("DELETE FROM detection_results WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM detection_results")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM detection_results")
    suspend fun getCount(): Int

    @Query("SELECT * FROM detection_results ORDER BY timestamp DESC LIMIT 1")
    fun getLatestDetection(): Flow<DetectionResultEntity?>

    // 알림 상태 업데이트
    @Query("UPDATE detection_results SET alertStatus = :status, alertType = :type, alertTimestamp = :timestamp, alertMessage = :message WHERE id = :id")
    suspend fun updateAlertStatus(id: Long, status: String, type: String, timestamp: Long, message: String)

    // L3 보호자 알림 상태 업데이트
    @Query("UPDATE detection_results SET guardianNotified = :notified, guardianNotificationType = :type WHERE id = :id")
    suspend fun updateGuardianNotification(id: Long, notified: Boolean, type: String)

    // 알림 히스토리 조회
    @Query("SELECT * FROM detection_results WHERE alertStatus = 'SHOWN' ORDER BY alertTimestamp DESC")
    fun getAlertHistory(): Flow<List<DetectionResultEntity>>

    // 미처리 알림 조회 (앱 시작 시 재시도용)
    @Query("SELECT * FROM detection_results WHERE alertStatus = 'PENDING' AND hazardScoreLevel IN ('L1', 'L2', 'L3') ORDER BY timestamp ASC")
    suspend fun getPendingAlerts(): List<DetectionResultEntity>
}
