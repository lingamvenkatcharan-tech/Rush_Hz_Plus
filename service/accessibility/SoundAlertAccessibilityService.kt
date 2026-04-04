package com.example.rush_hz_plus.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.rush_hz_plus.service.monitor.AudioMonitorService
import timber.log.Timber

/**
 * 접근성 제스처 기반 FGS 시작을 지원하는 서비스
 *
 * ⚠️ Android 14+ 정책 준수:
 * - 접근성 서비스 내에서 사용자 제스처 감지 → FGS 시작 허용
 * - 볼륨 키 조합 제스처: 볼륨 업 + 볼륨 다운 동시 길게 누르기
 */
class SoundAlertAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SoundAlertAccessibility"
        private const val GESTURE_TIMEOUT_MS = 300L // 제스처 인식 타임아웃
        private const val LONG_PRESS_THRESHOLD_MS = 1000L // 길게 누르기 임계값
    }

    // 볼륨 키 상태 추적
    private var isVolumeUpPressed = false
    private var isVolumeDownPressed = false
    private var volumeUpPressTime = 0L
    private var volumeDownPressTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var gestureDetectionRunnable: Runnable? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 접근성 이벤트 처리는 불필요 — 오디오 감지는 AudioMonitorService가 담당
        // 제스처 감지는 onKeyEvent()에서 처리
    }

    override fun onInterrupt() {
        // 시스템이 접근성 서비스를 일시 중단할 때 호출됨
        // 복구 로직 불필요 — AudioMonitorService는 독립 실행
        resetGestureState()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.tag(TAG).i("✅ 접근성 서비스 연결됨")
        // 선택적: 접근성 활성화 알림 표시
        showAccessibilityActivatedNotification()
    }

    /**
     * 키 이벤트 처리 — 볼륨 키 조합 제스처 감지
     *
     * 📌 Android 14+ 정책 허용:
     * - 접근성 서비스 내에서 사용자 제스처 감지 → FGS 시작 허용
     * - 볼륨 키 조합: 볼륨 업 + 볼륨 다운 동시 길게 누르기 (1초 이상)
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onKeyEvent(event: KeyEvent): Boolean {

        val keyCode = event.keyCode
        val action = event.action
        val currentTime = System.currentTimeMillis()

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                handleVolumeKey(KeyEvent.KEYCODE_VOLUME_UP, action, currentTime)
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                handleVolumeKey(KeyEvent.KEYCODE_VOLUME_DOWN, action, currentTime)
            }
            else -> return false
        }

        // 제스처 감지 시도
        detectVolumeGesture()

        // 이벤트 소비 여부: 제스처 감지 중이면 true, 아니면 false
        return gestureDetectionRunnable != null
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun handleVolumeKey(keyCode: Int, action: Int, currentTime: Long) {
        when (action) {
            KeyEvent.ACTION_DOWN -> {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    isVolumeUpPressed = true
                    volumeUpPressTime = currentTime
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    isVolumeDownPressed = true
                    volumeDownPressTime = currentTime
                }
            }
            KeyEvent.ACTION_UP -> {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    isVolumeUpPressed = false
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    isVolumeDownPressed = false
                }
                // 키 해제 시 제스처 감지 시도
                detectVolumeGesture()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun detectVolumeGesture() {
        // 기존 감지 러너블 취소
        gestureDetectionRunnable?.let { handler.removeCallbacks(it) }
        gestureDetectionRunnable = null

        // 두 키가 모두 눌려 있고, 충분한 시간 동안 누르고 있는지 확인
        if (isVolumeUpPressed && isVolumeDownPressed) {
            val pressDuration = System.currentTimeMillis() - maxOf(volumeUpPressTime, volumeDownPressTime)
            if (pressDuration >= LONG_PRESS_THRESHOLD_MS) {
                // 제스처 감지 성공
                handleVolumeGestureDetected()
                resetGestureState()
                return
            }

            // 아직 충분한 시간이 지나지 않았다면, 타임아웃 후 재검사
            gestureDetectionRunnable = Runnable {
                detectVolumeGesture()
            }
            handler.postDelayed(gestureDetectionRunnable!!, GESTURE_TIMEOUT_MS)
        } else {
            // 하나의 키만 눌려 있거나 모두 해제됨 → 상태 리셋
            resetGestureState()
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun handleVolumeGestureDetected() {
        Timber.tag(TAG).i("✅ 볼륨 키 조합 제스처 감지됨")

        // 권한 확인
        if (!hasRequiredPermissions()) {
            Timber.tag(TAG).w("❌ 필수 권한 부족 — FGS 시작 불가")
            showPermissionRequiredNotification()
            return
        }

        // FGS 시작 시도
        try {
            val intent = Intent(this, AudioMonitorService::class.java)
            startForegroundService(intent)
            Timber.tag(TAG).i("✅ AudioMonitorService 시작 성공 (접근성 제스처)")
            showMonitoringStartedNotification()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ FGS 시작 실패")
            showStartFailedNotification()
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun hasRequiredPermissions(): Boolean {
        val hasRecordAudio = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasFgsMic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return hasRecordAudio && hasFgsMic
    }

    private fun resetGestureState() {
        isVolumeUpPressed = false
        isVolumeDownPressed = false
        volumeUpPressTime = 0L
        volumeDownPressTime = 0L
        gestureDetectionRunnable?.let { handler.removeCallbacks(it) }
        gestureDetectionRunnable = null
    }

    // =========================
    // 알림 표시 메서드 (선택적)
    // =========================

    private fun showAccessibilityActivatedNotification() {
        // 선택적: 접근성 활성화 알림
        // 구현 필요 시 NotificationManager 사용
    }

    private fun showPermissionRequiredNotification() {
        // 선택적: 권한 필요 알림
        // 구현 필요 시 NotificationManager 사용
    }

    private fun showMonitoringStartedNotification() {
        // 선택적: 모니터링 시작 알림
        // 구현 필요 시 NotificationManager 사용
    }

    private fun showStartFailedNotification() {
        // 선택적: 시작 실패 알림
        // 구현 필요 시 NotificationManager 사용
    }
}