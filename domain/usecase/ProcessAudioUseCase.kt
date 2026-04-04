package com.example.rush_hz_plus.domain.usecase

import android.content.Context
import com.example.rush_hz_plus.data.repository.DetectionRepositoryImpl
import com.example.rush_hz_plus.data.repository.DetectionRepositoryInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import com.example.rush_hz_plus.domain.entity.DetectionResult
import com.example.rush_hz_plus.domain.score.ClassScorer
import com.example.rush_hz_plus.domain.score.HazardScore
import com.example.rush_hz_plus.domain.score.ScoreCalculator
import com.example.rush_hz_plus.service.alert.AlertManager
import com.example.rush_hz_plus.service.emergency.EmergencyManager
import com.example.rush_hz_plus.service.monitor.InferenceEngine


interface EmergencyCallback {
    fun onEmergencyDetected()
}

@Singleton
class ProcessAudioUseCase @Inject constructor(
    private val inferenceEngine: InferenceEngine,
    private val alertManager: AlertManager,
    private val emergencyManager: EmergencyManager,
    private val repository: DetectionRepositoryInterface,
    private val locationProvider: LocationProvider,
    private val userIdProvider: UserIdProvider,
    private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ProcessAudio"
        private const val REQUIRED_PCMS = 16000
        private const val LOC_TIMEOUT_MS = 2_000L

        // 무음 판단용 임계치 (장치마다 다를 수 있음)
        // ★ SILENCE_RMS_THRESHOLD 제거
        private const val CLAMP_SHORT_MAX = 32767.0
    }

    private val frameSeq = AtomicLong(0)
    private var emergencyCallback: EmergencyCallback? = null

    // 상태 관리 변수 추가
    private var currentHazardLevel = HazardScore.LEVEL_SAFE
    private var lastLevelUpTime = 0L
    private val MIN_LEVEL_HOLD_TIME = 5000L // L2/L3 진입 후 최소 5초 유지

    /**
     * 메인 오디오 처리 파이프라인
     * - Inference → Score → Persist → Branch(Alert/Emergency)
     * - 각 단계 독립 예외 처리로 부분 실패 복원
     * - 히스테리시스 적용: L2/L3 진입 후 일정 시간은 낮은 레벨로 전환 금지
     */
    suspend fun execute(pcmBuffer: ShortArray) {
        val seq = frameSeq.incrementAndGet()
        val enterTs = System.currentTimeMillis()

        Timber.tag(TAG).v(
            "➡️ execute(frame=%d) thread=%s bufLen=%d",
            seq, Thread.currentThread().name, pcmBuffer.size
        )

        if (pcmBuffer.size != REQUIRED_PCMS) {
            Timber.tag(TAG).w(
                "⚠️ frame=%d invalid pcm length=%d (required=%d) — skip",
                seq, pcmBuffer.size, REQUIRED_PCMS
            )
            return
        }

        val (rms, peak) = calcRmsAndPeak(pcmBuffer)
        Timber.tag(TAG).d(
            "🎚️ frame=%d PCM stats → RMS=%.1f  Peak=%.0f",
            seq, rms, peak
        )

        try {
            // ============================ 1) Inference ============================
            val infStart = System.currentTimeMillis()
            Timber.tag(TAG).i("🧠 frame=%d Inference START", seq)

            val inferenceResult = runCatching {
                inferenceEngine.runInference(pcmBuffer)
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "💥 frame=%d Inference FAILED", seq)
                return
            }.getOrThrow()

            val infElapsed = System.currentTimeMillis() - infStart
            val topIdx = inferenceResult.topClassIndex
            val topProb = inferenceResult.maxProbability
            Timber.tag(TAG).i(
                "✅ frame=%d Inference DONE in %dms → top=%s(%.2f) keyword=%s snr=%.1f",
                seq, infElapsed, ClassScorer.getLabel(topIdx), topProb,
                inferenceResult.hasKeyword, inferenceResult.snrScore
            )

            // ============================ 2) Location ============================
            val (lat, lng) = getLocationWithTimeout() ?: (0.0 to 0.0)

            // ============================ 3) User ============================
            val userId = runCatching { userIdProvider.getCurrentUserId() }
                .getOrDefault("")

            // ============================ 4) Score Calculation ============================
            val hazardScore = ScoreCalculator.calculate(
                aiConfidence = inferenceResult.maxProbability,
                hasKeyword = inferenceResult.hasKeyword,
                classIndex = inferenceResult.topClassIndex,
                snrScore = inferenceResult.snrScore,
                rms = rms,
                debug = ScoreCalculator.DebugCtx(
                    frameSeq = seq,
                    rms = rms,
                    peak = peak.toDouble(),
                    enterTimestampMs = enterTs,
                    callingThread = Thread.currentThread().name
                )
            )

            // ============================ 🔥 히스테리시스 적용 ============================
            val now = System.currentTimeMillis()
            val rawLevel = hazardScore.level
            val finalLevel = applyHysteresis(rawLevel, now)

            // ============================ 5) DetectionResult Assembly ============================
            val detectionResult = DetectionResult(
                id = 0,
                probabilities = inferenceResult.probabilities,
                detectedClass = inferenceResult.topClassIndex,
                keywordDetected = inferenceResult.hasKeyword,
                snr = inferenceResult.snrScore,
                hazardScore = HazardScore(
                    rawScore = hazardScore.rawScore,
                    normalizedScore = hazardScore.normalizedScore,
                    level = finalLevel, // 🔥 수정: 히스테리시스 반영된 레벨 사용
                    isEmergency = finalLevel == HazardScore.LEVEL_L3
                ),
                timestamp = System.currentTimeMillis(),
                userId = userId,
                soundLabel = ClassScorer.getLabel(inferenceResult.topClassIndex),
                latitude = lat,
                longitude = lng,
                alertStatus = "PENDING",
                alertType = finalLevel
            )

            // ============================ 6) Save → Get Real ID ============================
            var persistedDetectionResult = detectionResult
            runCatching {
                val insertedId = repository.submitDetectionResult(detectionResult)
                if (insertedId > 0) {
                    persistedDetectionResult = detectionResult.copy(id = insertedId)
                    Timber.tag(TAG).v("💽 frame=%d persisted to Room (id=%d)", seq, insertedId)
                } else {
                    Timber.tag(TAG).w("💽 frame=%d persist returned invalid id=%d", seq, insertedId)
                }
            }.onFailure {
                Timber.tag(TAG).e(it, "💽 frame=%d persist failed — continue flow", seq)
            }

            // ============================ 7) Logging ============================
            val (thL1, thL2, thL3) = ScoreCalculator.getCurrentThresholds()
            Timber.tag(TAG).d(
                """
                🧮 frame=%d Score Summary
                • Label = %s
                • NormScore = %.2f (Raw=%.2f, SNR=%.1f, Keyword=%s)
                • Level = %s → Final = %s
                • Thresholds(L1/L2/L3) = %.1f / %.1f / %.1f
                """.trimIndent(),
                seq,
                detectionResult.soundLabel,
                hazardScore.normalizedScore,
                hazardScore.rawScore,
                detectionResult.snr,
                detectionResult.keywordDetected,
                rawLevel,
                finalLevel, // 로그에 히스테리시스 적용 전/후 모두 표시
                thL1, thL2, thL3
            )

            // ============================ 8) Alert Branching (using finalLevel) ============================
            when (finalLevel) {
                HazardScore.LEVEL_L1,
                HazardScore.LEVEL_L2 -> {
                    Timber.tag(TAG).i("🔔 frame=%d Alert (L1/L2) [final]", seq)
                    appScope.launch {
                        try {
                            alertManager.triggerAlert(persistedDetectionResult)
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "AlertManager failed (L1/L2) frame=%d", seq)
                        }
                    }
                }

                HazardScore.LEVEL_L3 -> {
                    Timber.tag(TAG).w("🚨 frame=%d EMERGENCY (L3) [final]", seq)
                    appScope.launch {
                        try {
                            emergencyManager.triggerEmergency(persistedDetectionResult)
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "EmergencyManager failed (L3) frame=%d", seq)
                        }
                    }
                    runCatching { emergencyCallback?.onEmergencyDetected() }
                        .onFailure { Timber.tag(TAG).w(it, "EmergencyCallback error frame=%d", seq) }
                }

                HazardScore.LEVEL_SAFE -> {
                    Timber.tag(TAG).v("🟢 frame=%d SAFE — no alert [final]", seq)
                }
            }

            // ============================ 9) Summary ============================
            Timber.tag(TAG).d(
                "🏁 frame=%d done: Label=%s, RawLevel=%s → FinalLevel=%s, Total=%dms",
                seq,
                detectionResult.soundLabel,
                rawLevel,
                finalLevel,
                (System.currentTimeMillis() - enterTs)
            )

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ frame=%d pipeline failed — dropped", seq)
        }
    }

    // 🔥 히스테리시스 핵심 로직
    private fun applyHysteresis(rawLevel: String, now: Long): String {
        // 1. 상향 이동(L1/L2/L3 진입): 즉시 허용 + 타이머 갱신
        if (rawLevel in listOf(HazardScore.LEVEL_L1, HazardScore.LEVEL_L2, HazardScore.LEVEL_L3)) {
            if (rawLevel != currentHazardLevel) {
                // 레벨이 실제로 상승한 경우
                currentHazardLevel = rawLevel
                lastLevelUpTime = now
                Timber.tag(TAG).d("📈 레벨 상향: $rawLevel (타이머 갱신)")
            }
            return rawLevel
        }

        // 2. 하향 이동(SAFE로 전환 시도): 히스테리시스 적용
        if (rawLevel == HazardScore.LEVEL_SAFE) {
            // 현재 L2/L3이고, 최소 유지 시간이 지나지 않았다면 유지
            if (currentHazardLevel in listOf(HazardScore.LEVEL_L2, HazardScore.LEVEL_L3)) {
                if (now - lastLevelUpTime < MIN_LEVEL_HOLD_TIME) {
                    Timber.tag(TAG).d(
                        "⏳ 히스테리시스: $currentHazardLevel 유지 (경과: ${now - lastLevelUpTime}ms < $MIN_LEVEL_HOLD_TIME)"
                    )
                    return currentHazardLevel
                } else {
                    // 유지 시간 초과 → SAFE로 전환
                    currentHazardLevel = HazardScore.LEVEL_SAFE
                    Timber.tag(TAG).d("📉 레벨 하향: SAFE (유지 시간 만료)")
                    return HazardScore.LEVEL_SAFE
                }
            }
            // 현재 L1이거나 이미 SAFE인 경우: 즉시 SAFE
            currentHazardLevel = HazardScore.LEVEL_SAFE
            return HazardScore.LEVEL_SAFE
        }

        // 기본: 상태 유지 (예상치 못한 레벨)
        return currentHazardLevel
    }

    private suspend fun getLocationWithTimeout(): Pair<Double, Double>? {
        return try {
            withTimeoutOrNull(LOC_TIMEOUT_MS) { locationProvider.getCurrentLocation() }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Location get failed — fallback to (0,0)")
            null
        }
    }

    // ===== Utilities for logging input quality =====
    private fun calcRmsAndPeak(pcm: ShortArray): Pair<Double, Double> {
        var sumSq = 0.0
        var peak = 0
        for (s in pcm) {
            val v = s.toInt()
            val abs = kotlin.math.abs(v)
            if (abs > peak) peak = abs
            sumSq += (v * v).toDouble()
        }
        val meanSq = sumSq / pcm.size
        val rms = kotlin.math.sqrt(meanSq)
        return rms to peak.toDouble().coerceAtMost(CLAMP_SHORT_MAX)
    }
}
