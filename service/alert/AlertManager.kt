package com.example.rush_hz_plus.service.alert

import android.content.Context
import com.example.rush_hz_plus.core.utils.PreferenceManager
import com.example.rush_hz_plus.data.repository.DetectionRepositoryInterface
import com.example.rush_hz_plus.domain.entity.DetectionResult
import com.example.rush_hz_plus.domain.score.HazardScore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val flashAlert: FlashAlert,
    private val vibrationAlert: VibrationAlert,
    private val ttsManager: TTSManager,
    private val notificationHelper: NotificationHelper,
    private val preferenceManager: PreferenceManager,
    private val detectionRepository: DetectionRepositoryInterface
) {

    companion object {
        private const val TAG = "AlertManager"
    }

    private val detectionResult = DetectionResult

    /**
     * DetectionResult 기반 주 진입점
     * - 내부에서 executeAlert(...)로 위임하여 Notification/물리 경보/음성까지 일관 처리
     */
    suspend fun triggerAlert(detectionResult: DetectionResult) {
        executeAlert(detectionResult.hazardScore, detectionResult.soundLabel)

        // 알림 히스토리에 기록
        detectionRepository.updateAlertStatus(
            id = detectionResult.id,
            status = "SHOWN",
            type = detectionResult.hazardScore.level,
            timestamp = System.currentTimeMillis(),
            message = "위험 소리 감지: ${detectionResult.soundLabel}"
        )
    }

    /**
     * 위험 점수 기반 경고 실행
     * - L3는 EmergencyManager/FullScreenAlarmActivity가 전담 → 여기서는 즉시 리턴
     * - L1/L2만 Notification + (진동/플래시) + (선택적 TTS)
     */
    fun executeAlert(score: HazardScore, keywordOrLabel: String = "") {
        if (score.isL3()) {
            Timber.tag(TAG).d("L3 경보는 EmergencyManager/FullScreenAlarmActivity 전담 — AlertManager는 생략")
            return
        }

        Timber.tag(TAG).i("경고 실행 — level=${score.level}, label=$keywordOrLabel")

        // 1) 알림(Notification)
        try {
            notificationHelper.showAlert(score, keywordOrLabel)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Notification 표시 실패")
        }

        // 2) 물리 경고 (진동/플래시) — L1/L2만
        triggerPhysicalAlert(score.level)

        // 3) TTS — L1/L2 전용 (선택: 사용자 선호 설정이 없으면 기본 실행, 과도한 발화 방지는 isSpeaking으로 제어)
        try {
            // 필요 시 환경설정에 따르는 게 바람직하지만, 현재 제공된 인터페이스엔 L3용만 있어
            // L1/L2는 간단히 중복 방어만 적용합니다.
            if (!ttsManager.isSpeaking()) {
                // L1/L2는 시스템 메시지만 TTS 재생
                when (score.level) {
                    HazardScore.LEVEL_L1 -> {
                        ttsManager.speakCustomMessage("주의하세요. 약한 위험 신호가 감지되었습니다.")
                    }
                    HazardScore.LEVEL_L2 -> {
                        ttsManager.speakSystemAlert("경고! 위험 신호가 감지되었습니다.")
                    }
                }
            } else {
                Timber.tag(TAG).d("TTS 발화 중 — 중복 생략")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "L1/L2 TTS 발화 실패")
        }
    }


    /**
     * 물리적 경고 (진동 + 플래시)
     */
    private fun triggerPhysicalAlert(level: String) {
        when (level) {
            HazardScore.LEVEL_L1 -> {
                vibrationAlert.vibratePattern(VibrationPattern.LOW)
                flashAlert.blink(times = 2, delay = 300L)
                Timber.tag(TAG).d("L1 물리경고 실행 (LOW)")
            }
            HazardScore.LEVEL_L2 -> {
                vibrationAlert.vibratePattern(VibrationPattern.MEDIUM)
                flashAlert.blink(times = 4, delay = 200L)
                Timber.tag(TAG).d("L2 물리경고 실행 (MEDIUM)")
            }
            else -> {
                Timber.tag(TAG).v("물리경고 스킵: $level")
            }
        }
    }

    /**
     * 모든 경고 즉시 중지 (진동/플래시/TTS)
     * - FullScreenAlarmActivity가 종료 시 호출할 수 있음
     */
    fun stopAllAlerts() {
        try { vibrationAlert.stopVibration() } catch (e: Exception) { Timber.tag(TAG).w(e) }
        try { flashAlert.stopBlinking() } catch (e: Exception) { Timber.tag(TAG).w(e) }
        try { ttsManager.stop() } catch (e: Exception) { Timber.tag(TAG).w(e) }
    }


    // 체험용 데모
    fun demoVibration(pattern: VibrationPattern) = vibrationAlert.vibratePattern(pattern)
    fun demoFlash(blinkCount: Int = 5, delay: Long = 300L) = flashAlert.blink(blinkCount, delay)
}