// data/repository/DetectionRepositoryImpl.kt
package com.example.rush_hz_plus.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.withTransaction
import com.example.rush_hz_plus.data.local.AppDatabase
import com.example.rush_hz_plus.data.local.dao.DetectionDao
import com.example.rush_hz_plus.data.local.dao.GuardianNotificationDao
import com.example.rush_hz_plus.data.local.entity.DetectionResultEntity
import com.example.rush_hz_plus.domain.entity.DetectionResult
import com.example.rush_hz_plus.data.local.entity.GuardianNotificationEntity
import com.example.rush_hz_plus.data.model.GuardianNotificationStatus
import com.google.firebase.database.DatabaseReference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DetectionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context, // 어노테이션 추가
    private val appDatabase: AppDatabase, // ← Hilt가 제공
    private val logInfoRef: DatabaseReference, // DatabaseReference로 변경
    private val connectivityManager: ConnectivityManager
) : DetectionRepositoryInterface {

    // Hilt 제공 DB만 사용
    private val detectionDao: DetectionDao get() = appDatabase.detectionDao()
    private val guardianNotificationDao: GuardianNotificationDao get() = appDatabase.guardianNotificationDao()

    private val _emergencyEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 10)
    override val emergencyEvent: SharedFlow<Unit> = _emergencyEvent.asSharedFlow()

    private val _latestRealtimeDetection = MutableStateFlow<DetectionResult?>(null)
    override val latestRealtimeDetection: StateFlow<DetectionResult?> = _latestRealtimeDetection

    // 앱 생명주기와 동기화된 스코프
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun submitDetectionResult(result: DetectionResult): Long = withContext(Dispatchers.IO) {
        var insertedId: Long = -1L
        try {
            val entity = DetectionResultEntity.fromDomain(result)
            insertedId = detectionDao.insert(entity) // 실제 ID 획득

            withContext(Dispatchers.Main.immediate) {
                // ID를 포함한 새 DetectionResult 생성
                val resultWithId = result.copy(id = insertedId)
                _latestRealtimeDetection.value = resultWithId
            }

            return@withContext insertedId
        } catch (e: Exception) {
            Timber.e(e, "DB insert failed")
            withContext(Dispatchers.Main.immediate) {
                _latestRealtimeDetection.value = result // 실패해도 UI는 반영 (id=0 유지)
            }
            return@withContext -1L // 실패 시 -1 반환
        }
    }

    // 알림 상태 업데이트 구현
    override suspend fun updateAlertStatus(id: Long, status: String, type: String, timestamp: Long, message: String) {
        detectionDao.updateAlertStatus(id, status, type, timestamp, message)
    }

    // 보호자별 알림 상태 저장
    override suspend fun updateGuardianNotification(
        id: Long,
        guardianId: String,
        notified: Boolean,
        type: String
    ) {
        val entity = GuardianNotificationEntity(
            detectionId = id,
            guardianId = guardianId,
            notified = notified,
            type = type,
            timestamp = System.currentTimeMillis()
        )
        guardianNotificationDao.insert(entity)

        // 개선: notified 여부 + type을 함께 기록
        val status = if (notified) "✅" else "❌"
        Timber.d("보호자 알림 상태 저장됨: $status detection=$id, guardian=$guardianId, type=$type")
    }

    override suspend fun getGuardianNotificationStatus(
        detectionId: Long,
        guardianId: String
    ): GuardianNotificationStatus? = withContext(Dispatchers.IO) {
        val entity = guardianNotificationDao.getNotification(detectionId, guardianId)
        entity?.let {
            GuardianNotificationStatus(
                detectionId = it.detectionId,
                guardianId = it.guardianId,
                notified = it.notified,
                type = it.type,
                timestamp = it.timestamp
            )
        }
    }

    // 알림 히스토리 조회 구현
    override fun getAlertHistory(): Flow<List<DetectionResult>> {
        return detectionDao.getAlertHistory().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    // 미처리 알림 조회 구현
    override suspend fun getPendingAlerts(): List<DetectionResult> {
        return detectionDao.getPendingAlerts().map { it.toDomain() }
    }

    override suspend fun syncToRtdb(entity: DetectionResultEntity) = withContext(Dispatchers.IO) {
        if (!isNetworkConnected()) {
            Timber.w("RTDB", "네트워크 없음 - 동기화 건너뜀")
            return@withContext
        }

        try {
            val logData = mapOf(
                "created_date" to formatDate(entity.timestamp),
                "location" to "${entity.latitude},${entity.longitude}",
                "sound_name" to entity.soundLabel,
                "user_id" to entity.userId,
                "raw_score" to entity.hazardScoreRawScore,
                "keyword_detected" to entity.keywordDetected
            )

            logInfoRef.push().setValue(logData).await()

            // 트랜잭션으로 원자적 업데이트
            appDatabase.withTransaction {
                detectionDao.markAsSyncedById(entity.id)
            }
            Timber.d("Marked as synced: ${entity.id}")

        } catch (e: Exception) {
            Timber.tag("RTDB").e(e, "Sync failed for id=${entity.id}")
            // 실패 시 로컬 DB 상태 유지 (재시도를 위해)
            throw e
        }
    }

    override suspend fun syncPendingDetections() = withContext(Dispatchers.IO) {
        val pending = detectionDao.getUnsyncedResults()
        pending.forEach { entity ->
            try {
                syncToRtdb(entity)
            } catch (e: Exception) {
                Timber.tag("RTDB").w(e, "건너뛰기: id=${entity.id}")
                // 개별 실패는 전체 중단하지 않음
            }
        }
    }

    private fun isNetworkConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun getLatestDetection(): Flow<DetectionResult?> =
        detectionDao.getLatestDetection().map { it?.toDomain() }

    override fun getAllDetections(): Flow<List<DetectionResult>> =
        detectionDao.getAllResults().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getUnsyncedCount(): Flow<Int> = detectionDao.getUnsyncedCount()

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }

    // 외부에서 호출 가능한 정리 메서드
    fun close() {
        repositoryScope.cancel()
    }
}