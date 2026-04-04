// domain/score/SnrCalculator.kt
package com.example.rush_hz_plus.domain.score

import kotlin.math.pow
import kotlin.math.sqrt

object SnrCalculator {

    // 절대 RMS 기준 (경험적 튜닝 필요)
    private const val RMS_LOW = 100.0    // 조용한 실내
    private const val RMS_MED = 1000.0   // 일반 대화
    private const val RMS_HIGH = 3000.0  // 사이렌/경보 수준 (귀하 로그 값: ~3290)

    // 이동 평균 윈도우
    private const val WINDOW_SIZE = 20
    private val rmsHistory = ArrayDeque<Double>(WINDOW_SIZE)

    fun calculate(buffer: ShortArray): Float {
        // 1. 현재 RMS
        val currentRms = buffer.rms()

        // 2. 이력 업데이트
        if (rmsHistory.size >= WINDOW_SIZE) rmsHistory.removeFirst()
        rmsHistory.addLast(currentRms)

        // 3. 이력 평균 & 표준편차 (선택)
        val avgRms = if (rmsHistory.isNotEmpty()) rmsHistory.average() else 1.0

        // 1) 절대 강도 점수: RMS 크기에 따라 0~10
        val absScore = when {
            currentRms >= RMS_HIGH -> 10f
            currentRms >= RMS_MED  -> 5f
            currentRms >= RMS_LOW  -> 2f
            else -> 0f
        }

        // 2) 상대적 변화 점수: 급격한 상승 (ΔRMS) 감지
        val deltaScore = if (rmsHistory.size > 1) {
            val prevAvg = rmsHistory.dropLast(1).averageOrNull() ?: avgRms
            val riseRatio = if (prevAvg > 0) (avgRms - prevAvg) / prevAvg else 0.0
            when {
                riseRatio > 0.8 -> 5f   // 80% 이상 급상승 (사이렌 시작)
                riseRatio > 0.5 -> 3f
                riseRatio < -0.5 -> -3f // 갑작스러운 정적 (의심스러움)
                else -> 0f
            }
        } else {
            0f
        }

        // 최종 SNR 점수: 절대 + 변화 (범위: -3 ~ 15 → clamp to [-10, 10])
        val rawScore = absScore + deltaScore
        return rawScore.coerceIn(-10f, 10f)
    }

    // 유틸 확장
    private fun ShortArray.rms(): Double {
        var sumSq = 0.0
        for (s in this) sumSq += s.toDouble().pow(2.0)
        return sqrt(sumSq / this.size)
    }

    private fun List<Double>.averageOrNull(): Double? =
        if (isEmpty()) null else average()
}