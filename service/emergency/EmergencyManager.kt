package com.example.rush_hz_plus.service.emergency

import android.content.Context
import android.widget.Toast
import com.example.rush_hz_plus.core.utils.SmsFallbackManager.sendSmsFallback
import com.example.rush_hz_plus.data.repository.DetectionRepositoryInterface
import com.example.rush_hz_plus.domain.entity.DetectionResult
import com.example.rush_hz_plus.domain.usecase.LocationProvider
import com.example.rush_hz_plus.domain.usecase.UserIdProvider
import com.example.rush_hz_plus.service.alert.AlertManager
import com.example.rush_hz_plus.service.alert.TTSManager
import com.example.rush_hz_plus.service.system.ForegroundServiceManager
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ktx.getValue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: DatabaseReference,
    private val contactManager: EmergencyContactManager,
    private val smsHelper: SMSHelper,
    private val locationProvider: LocationProvider,
    private val userIdProvider: UserIdProvider,
    private val alertManager: AlertManager,
    private val foregroundServiceManager: ForegroundServiceManager,
    private val ttsManager: TTSManager,
    private val appScope: CoroutineScope,
    private val detectionRepository: DetectionRepositoryInterface
) {


    companion object {
        private const val TAG = "EmergencyManager"
        private const val EMERGENCY_TIMEOUT = 5000L
        private const val L3_COOLDOWN_MS = 10_000L
    }

    private val cooldownMutex = Mutex()
    private var lastEmergencyTime = 0L

    /**
     * 긴급(L3) 알림 트리거
     */
    suspend fun triggerEmergency(detectionResult: DetectionResult) {
        val now = System.currentTimeMillis()
        cooldownMutex.withLock {
            if (now - lastEmergencyTime < L3_COOLDOWN_MS) {
                Timber.w(TAG, "⚠️ L3 중복 감지 — ${L3_COOLDOWN_MS}ms 내 재호출 무시")
                return
            }
            lastEmergencyTime = now
        }

        val userId = userIdProvider.getCurrentUserId()
        if (userId == "알 수 없는 사용자") {
            Timber.w(TAG, "익명 사용자 — 긴급 알림 생략")
            return
        }

        val score = detectionResult.hazardScore
        if (!score.isL3()) return

        val nickname = getUserNickname(userId)
        val systemMessage = "${nickname}님, ${detectionResult.soundLabel} 소리가 감지되었습니다. 즉시 주변을 확인하세요."

        // 1️⃣ 커스텀 메시지 비동기 로드
        val customMessage = withTimeoutOrNull(2000L) {
            getLatestAutoMessage(userId)
        }

        // 2️⃣ TTS 재생
        appScope.launch {
            try {
                if (!customMessage.isNullOrBlank()) {
                    ttsManager.speakCustomMessage(customMessage)
                    Timber.d(TAG, "사용자 커스텀 메시지 발화: $customMessage")
                }
                delay(3000L)
                ttsManager.speakCustomMessage(systemMessage)
                Timber.d(TAG, "시스템 메시지 발화: $systemMessage")
            } catch (e: Exception) {
                Timber.e(e, "긴급 TTS 발화 실패")
            }
        }

        // 3️⃣ FullScreenAlarmActivity 실행 (UI 변경 없음)
        try {
            foregroundServiceManager.showFullScreenAlarm(systemMessage)
        } catch (e: Exception) {
            Timber.e(e, "FullScreenAlarmActivity 실행 실패")
            alertManager.executeAlert(score, detectionResult.soundLabel)
        }

        // L3 알림 상태 업데이트
        detectionRepository.updateAlertStatus(
            id = detectionResult.id,
            status = "SHOWN",
            type = "L3",
            timestamp = System.currentTimeMillis(),
            message = "긴급: ${detectionResult.soundLabel} 감지"
        )

        // 4️⃣ 위치 & 보호자 조회
        val location = withTimeoutOrNull(EMERGENCY_TIMEOUT) {
            locationProvider.getLastKnownLocation()
        }

        val guardians = try {
            contactManager.getGuardiansOfCurrentUser()
        } catch (e: Exception) {
            Timber.e(e, "보호자 조회 실패")
            emptyList()
        }

        val userNickname = getUserNickname(userId)

        // === 보호자 알림 전략: 3중 fallback (모두 동시 실행) ===

        // 1️⃣ 자동 전화 → 보호자 폰 진동/벨소리 유도 (기본 SMS 제약 없음)
        val guardianPhones = guardians.mapNotNull { it.phoneNumber.takeIf { it.isNotBlank() } }
        if (guardianPhones.isNotEmpty()) {
            EmergencyCallService.start(context, guardianPhones)
        }

        // 2️⃣ SMS 위임 (기본 SMS 앱 여부와 무관하게 ACTION_SENDTO 위임만 수행)
        guardians.forEach { guardian ->
            val guardianId = guardian.uid ?: guardian.phoneNumber
            sendSmsToGuardian(
                guardian = guardian,
                detectionResult = detectionResult,
                location = location,
                userNickname = userNickname,
                detectionId = detectionResult.id,
                guardianId = guardianId
            )
        }

        // 3️⃣ 앱 알림 (보호자도 앱 설치 시)
        guardians.filter { it.isAppUser }.forEach { guardian ->
            sendLocalAlertToGuardian(guardian, detectionResult, location, userNickname)
            detectionRepository.updateGuardianNotification(
                id = detectionResult.id,
                guardianId = guardian.uid ?: guardian.phoneNumber,
                notified = true,
                type = "APP"
            )
        }

        // 최종 사용자 피드백 — 토스트 (FullScreenAlarmActivity UI 불변)
        Toast.makeText(
            context,
            "보호자 ${guardians.size}명에게 전화 + SMS 알림 시도 중",
            Toast.LENGTH_LONG
        ).show()
    }

    private suspend fun getLatestAutoMessage(userId: String): String? {
        return try {
            val snapshot = database.child("users").child(userId).child("auto_messages").get().await()
            val messages = snapshot.children.mapNotNull { it.child("content").toString() }
            messages.lastOrNull()
        } catch (e: Exception) {
            Timber.e(e, "커스텀 자동 메시지 불러오기 실패")
            null
        }
    }

    private suspend fun getUserNickname(userId: String): String {
        return try {
            val snapshot = database.child("users").child(userId).child("nickname").get().await()
            snapshot.value?.toString() ?: "사용자"
        } catch (e: Exception) {
            Timber.e(e, "닉네임 조회 실패")
            "사용자"
        }
    }

    private fun sendLocalAlertToGuardian(
        guardian: GuardianInfo,
        detectionResult: DetectionResult,
        location: Pair<Double, Double>?,
        userNickname: String
    ) {
        val guardianUid = guardian.uid ?: return
        val alertId = database.child("guardian_alerts").child(guardianUid).push().key ?: return

        val title = "긴급: ${detectionResult.soundLabel} 감지"
        val body = "${userNickname}님의 위치에서 긴급 상황이 감지되었습니다."

        val alertData = mapOf(
            "timestamp" to ServerValue.TIMESTAMP,
            "userId" to userIdProvider.getCurrentUserId(),
            "userNickname" to userNickname,
            "title" to title,
            "body" to body,
            "soundLabel" to detectionResult.soundLabel,
            "latitude" to (location?.first ?: ""),
            "longitude" to (location?.second ?: ""),
            "status" to "pending"
        )

        database.child("guardian_alerts")
            .child(guardianUid)
            .child(alertId)
            .setValue(alertData)
            .addOnSuccessListener { Timber.d(TAG, "앱 보호자 알림 저장 완료: ${guardian.phoneNumber}") }
            .addOnFailureListener { e -> Timber.e(e, "앱 보호자 알림 저장 실패") }
    }

    // 핵심 수정: 직접 SMS 발송 제거 → 오직 SMS 위임만 수행
    private fun sendSmsToGuardian(
        guardian: GuardianInfo,
        detectionResult: DetectionResult,
        location: Pair<Double, Double>?,
        userNickname: String,
        detectionId: Long,
        guardianId: String
    ) {
        // 1. SMS 권한 없으면 즉시 실패 기록
        if (!smsHelper.hasSmsPermission()) {
            Timber.e(TAG, "SMS 권한 없음 — DB에 실패로 기록: ${guardian.phoneNumber}")
            appScope.launch {
                detectionRepository.updateGuardianNotification(
                    id = detectionId,
                    guardianId = guardianId,
                    notified = false,
                    type = "SMS_FAILED_PERMISSION"
                )
            }
            return
        }

        // 2. 메시지 구성 (위도/경도 그대로 출력 — 주소 변환 없음)
        val message = buildString {
            append("🚨 Hz+ 긴급 알림\n")
            append("사용자: $userNickname\n")
            append("위험: ${detectionResult.soundLabel} 감지됨\n")
            if (location != null) {
                append("위치: 위도 ${"%.6f".format(location.first)}, 경도 ${"%.6f".format(location.second)}\n")
            }
            append("지금 즉시 연락해 주세요.")
        }

        // 3. SMS 위임 실행 — 반환값을 명시적으로 Boolean으로 받음
        val fallbackLaunched: Boolean = sendSmsFallback(
            context = context,
            phoneNumber = guardian.phoneNumber,
            message = message,
            detectionId = detectionId
        ) { isSuccess: Boolean, type: String ->
            // 콜백 내부: 기록 갱신
            appScope.launch {
                detectionRepository.updateGuardianNotification(
                    id = detectionId,
                    guardianId = guardianId,
                    notified = isSuccess,
                    type = type
                )
            }
        }

        // 4. 위임 자체 실패 시 기록
        if (!fallbackLaunched) {
            Timber.e(TAG, "SMS 위임 시도조차 실패 → $guardianId")
            appScope.launch {
                detectionRepository.updateGuardianNotification(
                    id = detectionId,
                    guardianId = guardianId,
                    notified = false,
                    type = "SMS_NO_FALLBACK_APP"
                )
            }
        }
    }
}
