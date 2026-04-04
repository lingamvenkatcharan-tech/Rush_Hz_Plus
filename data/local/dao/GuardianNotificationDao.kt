package com.example.rush_hz_plus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.rush_hz_plus.data.local.entity.GuardianNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GuardianNotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: GuardianNotificationEntity)

    @Query("SELECT * FROM guardian_notifications WHERE detectionId = :detectionId")
    fun getNotificationsByDetectionId(detectionId: Long): Flow<List<GuardianNotificationEntity>>

    @Query("SELECT * FROM guardian_notifications WHERE detectionId = :detectionId")
    suspend fun getNotificationsByDetectionIdSync(detectionId: Long): List<GuardianNotificationEntity>

    @Query("DELETE FROM guardian_notifications WHERE detectionId = :detectionId")
    suspend fun deleteByDetectionId(detectionId: Long)

    // 특정 보호자-감지 쌍의 알림 상태 조회 (단일)
    @Query(
        """
        SELECT * FROM guardian_notifications 
        WHERE detectionId = :detectionId AND guardianId = :guardianId 
        ORDER BY timestamp DESC 
        LIMIT 1
        """
    )
    suspend fun getNotification(detectionId: Long, guardianId: String): GuardianNotificationEntity?
}