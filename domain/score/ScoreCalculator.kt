package com.example.rush_hz_plus.domain.score

import timber.log.Timber

object ScoreCalculator {

    // ==============================
    // 🔧 점수 구성 요소 가중치
    // ==============================
    // [수정] AI 의존도를 낮추고, Class 위험도 비중을 높여 균형 맞춤
    private const val AI_WEIGHT = 0.40f      // ↓
    private const val CLASS_WEIGHT = 0.35f   // ↓
    private const val SNR_WEIGHT = 0.15f     // ↑ (0.05 → 0.15)
    private const val KEYWORD_WEIGHT = 0.10f // 유지

    // ==============================
    // 🔧 고정 임계값
    // ==============================
    private const val THRESHOLD_L1 = 40f    // 주의 필요 (↓), 60f -> 40f
    private const val THRESHOLD_L2 = 60f    // 경고 (↓), 75f -> 60f
    private const val THRESHOLD_L3 = 80f    // 긴급 (↓), 90f -> 80f

    // ==============================
    // 🔧 필터링 임계값
    // ==============================
    // [수정] 약한 신호도 감지할 수 있도록 필터링 기준 완화
    private const val MIN_SNR_FOR_ALERT = 1.0f  // SNR 1.0 미만은 무시 (↓)
    private const val MIN_AI_CONFIDENCE = 0.15f // AI 신뢰도 15% 미만은 무시 (↓)
    // ★ MIN_RMS_FOR_ALERT 제거

    // ==============================
    // 🔧 클래스 점수 정규화 범위
    // ==============================
    private const val MAX_CLASS_SCORE = 30f  // ClassScorer의 최대 점수

    // ==============================
    // 🔧 Debug Context 정의
    // ==============================
    data class DebugCtx(
        val frameSeq: Long? = null,
        val rms: Double? = null,
        val peak: Double? = null,
        val enterTimestampMs: Long? = null,
        val callingThread: String? = Thread.currentThread().name
    )

    // ==============================
    // 🔧 디버그 컨텍스트 포함 calculate
    // ==============================
    fun calculate(
        aiConfidence: Float,
        hasKeyword: Boolean,
        classIndex: Int,
        snrScore: Float,
        rms: Double = 0.0,           // ← 기본값 추가
        debug: DebugCtx? = null      // ← 기본값 추가
    ): HazardScore {
        // ★ SAFE 클래스는 즉시 SAFE 반환
        if (classIndex == 0) {
            return HazardScore.safeDefault()
        }

        // 1️⃣ 사전 필터링: 완전 무음/저신뢰도 상황 차단
        val shouldFilterOut =
            aiConfidence < MIN_AI_CONFIDENCE

        if (shouldFilterOut) {
            return HazardScore.safeDefault()
        }

        // 2️⃣ SNR 기반 필터링
        if (snrScore < MIN_SNR_FOR_ALERT) {
            return HazardScore.safeDefault()
        }

        // 3️⃣ 구성요소별 정규화 점수 계산
        val normalizedAi = aiConfidence.coerceIn(0f, 1f)
        val classScore = ClassScorer.getScore(classIndex).toFloat()
        val normalizedClass = (classScore / MAX_CLASS_SCORE).coerceIn(0f, 1f)
        val normalizedKeyword = if (hasKeyword) 1.0f else 0.0f
        val normalizedSnr = ((snrScore.coerceIn(-10f, 10f) + 10f) / 20f)

        // 4️⃣ 가중치 적용 점수 계산
        val weightedScore = (
                normalizedAi * AI_WEIGHT +
                        normalizedClass * CLASS_WEIGHT +
                        normalizedKeyword * KEYWORD_WEIGHT +
                        normalizedSnr * SNR_WEIGHT
                ).coerceIn(0f, 1f)

        // 5️⃣ 0~100 정규화 및 등급 판정
        val normalizedScore = weightedScore * 100f
        val level = when {
            normalizedScore >= THRESHOLD_L3 -> HazardScore.LEVEL_L3
            normalizedScore >= THRESHOLD_L2 -> HazardScore.LEVEL_L2
            normalizedScore >= THRESHOLD_L1 -> HazardScore.LEVEL_L1
            else -> HazardScore.LEVEL_SAFE
        }

        val isEmergency = level == HazardScore.LEVEL_L3

        logDebugInfo(
            aiConfidence, hasKeyword, classIndex, snrScore, rms,
            normalizedAi, normalizedClass, normalizedKeyword, normalizedSnr,
            weightedScore, normalizedScore, level, debug
        )

        return HazardScore(
            rawScore = weightedScore * 100f,
            normalizedScore = normalizedScore,
            level = level,
            isEmergency = isEmergency
        )
    }

    private fun logDebugInfo(
        aiConfidence: Float, hasKeyword: Boolean, classIndex: Int, snrScore: Float,
        rms: Double, normAi: Float, normClass: Float, normKeyword: Float,
        normSnr: Float, weightedScore: Float, normalizedScore: Float,
        level: String,
        debug: DebugCtx?
    ) {
        val label = ClassScorer.getLabel(classIndex)
        val classScore = ClassScorer.getScore(classIndex)
        val sb = StringBuilder().apply {
            appendLine("[HazardScore]")
            debug?.frameSeq?.let { appendLine("• frame = $it") }
            debug?.callingThread?.let { appendLine("• thread = $it") }
            debug?.rms?.let { r -> appendLine("• input RMS = ${"%.1f".format(r)}") }
            debug?.peak?.let { p -> appendLine("• input Peak = ${"%.0f".format(p)}") }
            debug?.enterTimestampMs?.let { t0 ->
                val took = System.currentTimeMillis() - t0
                appendLine("• pipeline elapsed ≈ ${took}ms")
            }
            appendLine("• AI Confidence = ${"%.3f".format(aiConfidence)} (norm=${"%.3f".format(normAi)})")
            appendLine("• Keyword Detected = $hasKeyword (norm=${"%.3f".format(normKeyword)})")
            appendLine("• Class($label) Score = $classScore/30 (norm=${"%.3f".format(normClass)})")
            appendLine("• SNR = ${"%.1f".format(snrScore)} (norm=${"%.3f".format(normSnr)})")
            appendLine("• Weighted Score = ${"%.3f".format(weightedScore)} → Norm = ${"%.1f".format(normalizedScore)}")
            // [수정] 로그에 수정된 임계값 반영
            appendLine("• Level = $level")
            appendLine("• Fixed Thresholds → L1: $THRESHOLD_L1, L2: $THRESHOLD_L2, L3: $THRESHOLD_L3")
        }
        Timber.tag("ScoreCalc").d(sb.toString().trimEnd())
    }

    // Debugging용 Threshold getter
    fun getCurrentThresholds(): Triple<Float, Float, Float> =
        Triple(THRESHOLD_L1, THRESHOLD_L2, THRESHOLD_L3)
}