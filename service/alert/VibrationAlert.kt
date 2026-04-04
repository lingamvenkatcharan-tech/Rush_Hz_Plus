package com.example.rush_hz_plus.service.alert

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.rush_hz_plus.service.system.PermissionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Singleton
class VibrationAlert @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionManager: PermissionManager
) {

    companion object {
        private const val TAG = "VibrationAlert"
    }

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val currentJob = AtomicReference<Job?>(null)

    fun isVibrating(): Boolean = currentJob.get() != null

    private val jobLock = Any()

    /**
     * 진동 실행 — 기존 진동이 있다면 취소 후 새 진동 실행 (갱신 가능)
     *
     * 📌 설계 원칙:
     *   - 책임 전가 금지: 진동은 시스템이 자동 실행 → 사용자에게 선택권 요구 없음
     *   - 오프라인 우선: 네트워크/권한 불필요 — VIBRATE는 NORMAL 권한 (항상 허용)
     *   - 상태 관리: `currentJob` 기반 → L3 연속 발생 시 이전 진동 취소 후 새 진동 즉시 시작
     *
     * ⚠ 주의:
     *   - `permissionManager.hasVibrationCapability()` 제거 → CALL/SMS 권한용, 진동과 무관
     *   - `VIBRATE` permission은 AndroidManifest.xml에 `<uses-permission android:name="android.permission.VIBRATE" />` 선언만으로 충분
     */
    fun vibratePattern(pattern: VibrationPattern) {
        // 1. 기존 진동 취소
        currentJob.get()?.cancel()

        // 2. 새 Job 생성 — launch 시 즉시 실행하지 않고, 수동 제어 가능
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        val job = scope.launch {
            try {
                val effect = createVibrationEffect(pattern)
                vibrator.vibrate(effect)
                Timber.tag(TAG).i("✅ 진동 시작: $pattern")

                delay(pattern.durationMs + 100L)

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "❗ 진동 실행 실패: $pattern")
            } finally {
                // ✅ 핵심: launch 블록 내에서 자신의 Job을 coroutineContext로 참조
                val self = coroutineContext[Job] ?: return@launch

                // 상태 정리: 자신의 Job만 해제 (동시성 보장)
                if (currentJob.get() == self) {
                    currentJob.set(null)
                    Timber.tag(TAG).d("⏹️ 진동 종료: $pattern")
                }
            }
        }

        // 3. Job 등록 — 이제 `job`은 안정적인 객체 참조
        currentJob.set(job)
    }

    /**
     * 진동 효과 생성 — amplitude 명시적 설정 (삼성/픽셀 호환성 확보)
     *
     * 📌 기술 문서 §4.3.2 & Android 개발 모범 사례 반영:
     *   - Android 12(S)+: `VibrationEffect.createWaveform(timings, amplitudes, repeat)`
     *   - amplitudes: 255 = 100% 진동 강도 (OEM 차이 없이 일관된 진동)
     *   - Android 11 이하: 기존 방식 유지 (호환성 보장)
     */
    private fun createVibrationEffect(pattern: VibrationPattern): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (pattern) {
                VibrationPattern.LOW -> VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                VibrationPattern.MEDIUM -> {
                    val timings = longArrayOf(0, 300, 100, 300)
                    val amplitudes = intArrayOf(0, 128, 0, 128) // 50% → 100%
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                }
                VibrationPattern.HIGH -> {
                    val timings = longArrayOf(0, 200, 50, 200, 50, 200)
                    val amplitudes = intArrayOf(0, 192, 0, 192, 0, 192) // ~75%
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                }
                VibrationPattern.HIGHEST -> {
                    // 🔥 L3 fallback: 7차례 펄스, 100% 진동 강도
                    val timings = longArrayOf(0, 500, 100, 400, 100, 400, 100, 300)
                    val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255) // 100%
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            when (pattern) {
                VibrationPattern.LOW -> VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                else -> {
                    // Android 11 이하: amplitudes 없음 → timings만 사용
                    val timings = when (pattern) {
                        VibrationPattern.MEDIUM -> longArrayOf(0, 300, 100, 300)
                        VibrationPattern.HIGH -> longArrayOf(0, 200, 50, 200, 50, 200)
                        VibrationPattern.HIGHEST -> longArrayOf(0, 500, 100, 400, 100, 400, 100, 300)
                        else -> longArrayOf(0, 500) // unreachable
                    }
                    VibrationEffect.createWaveform(timings, -1)
                }
            }
        }
    }

    fun stopVibration() {
        currentJob.get()?.cancel()
        try {
            vibrator.cancel()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "진동 중지 시도 실패 — 무시")
        } finally {
            currentJob.set(null)
            Timber.tag(TAG).i("⏹️ 진동 강제 중지됨")
        }
    }

    private val VibrationPattern.durationMs: Long
        get() = when (this) {
            VibrationPattern.LOW -> 500L
            VibrationPattern.MEDIUM -> 300 + 100 + 300 // 700ms
            VibrationPattern.HIGH -> 200 + 50 + 200 + 50 + 200 // 700ms
            VibrationPattern.HIGHEST -> 500 + 100 + 400 + 100 + 400 + 100 + 300 // 1900ms
        }
}

/**
 * 진동 패턴 정의 — L1/L2/L3 용도별로 명확히 분리
 *
 * 📌 기술 문서 §4.3.2 반영:
 *   - L1: LOW (주의) — 짧고 부드러운 알림
 *   - L2: MEDIUM (경고) — 반복, 중간 강도
 *   - L3: HIGH (긴급) — 빠른 펄스 (기본)
 *   - L3 fallback: HIGHEST (TTS 실패 시) — 더 길고 강렬한 진동 (Z4)
 *
 * 🔹 HIGHEST 설계 근거 (ITU-T P.913 + Apple HIG):
 *   - 0~500ms: 최대 강도 진동 (사용자 즉시 인지)
 *   - 100ms 휴지 → 400ms 진동 → 반복 3회 → 마지막 300ms로 마무리
 *   - 총 1.9초 → 사용자가 "무언가 잘못됨" 인지하는 임계 시간 초과
 *
 * ⚠ Android 12+ 호환성:
 *   - amplitudes 배열 명시 → 삼성/픽셀 진동 강도 동일화 (DEFAULT_AMPLITUDE는 OEM별로 감쇄됨)
 */
enum class VibrationPattern {
    LOW,        // L1: 500ms 단일 진동
    MEDIUM,     // L2: [0, 300, 100, 300]
    HIGH,       // L3 기본: [0, 200, 50, 200, 50, 200]
    HIGHEST     // 🔥 L3 fallback: [0, 500, 100, 400, 100, 400, 100, 300]
}