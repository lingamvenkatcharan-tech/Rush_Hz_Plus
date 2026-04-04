// service/monitor/InferenceEngine.kt
package com.example.rush_hz_plus.service.monitor

import com.example.rush_hz_plus.domain.entity.InferenceResult
import com.example.rush_hz_plus.domain.score.ClassScorer
import com.example.rush_hz_plus.domain.score.KeywordDetector
import com.example.rush_hz_plus.domain.score.SnrCalculator
import com.example.rush_hz_plus.model.TFLiteInterpreter
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject


class InferenceEngine @Inject constructor(
    private val tfliteInterpreter: TFLiteInterpreter,
    private val keywordDetector: KeywordDetector
) {
    private val runSeq = java.util.concurrent.atomic.AtomicLong(0)

    /**
     * 1초 PCM(16k) → (TFLite 분류 확률, 키워드, SNR) 종합 결과
     * - TFLite & 키워드 검출을 병렬 실행
     * - 각 단계 로깅 강화
     */
    suspend fun runInference(pcmBuffer: ShortArray): InferenceResult =
        withContext(Dispatchers.Default.limitedParallelism(2)) {
            val seq = runSeq.incrementAndGet()
            val start = System.currentTimeMillis()

            try {
                Timber.v("🚀 Inference start [seq=%d, thread=%s]", seq, Thread.currentThread().name)

                // 병렬 처리: TFLite 분류 + 키워드 탐지
                val tfliteJob = async { tfliteInterpreter.runInference(pcmBuffer) }
                val voskJob = async { keywordDetector.detectKeywords(pcmBuffer) }

                // 동기 처리: SNR(가벼움)
                val snrScore = SnrCalculator.calculate(pcmBuffer)

                val probabilities = tfliteJob.await()
                val (hasKeyword, keywordText) = voskJob.await()

                // 최댓값 클래스/확률
                var maxIdx = 0
                var maxVal = Float.NEGATIVE_INFINITY
                for (i in probabilities.indices) {
                    if (probabilities[i] > maxVal) {
                        maxVal = probabilities[i]
                        maxIdx = i
                    }
                }

                val elapsed = System.currentTimeMillis() - start
                Timber.d(
                    """
                    🧩 Inference Summary [seq=%d]
                    • Predicted: %s (P=%.3f)
                    • Keyword: %s (%s)
                    • SNR: %.1f
                    • Time: %d ms
                    """.trimIndent(),
                    seq,
                    ClassScorer.getLabel(maxIdx), maxVal,
                    hasKeyword, if (hasKeyword) keywordText else "",
                    snrScore,
                    elapsed
                )

                InferenceResult(
                    probabilities = probabilities,
                    maxProbability = maxVal,
                    topClassIndex = maxIdx,
                    hasKeyword = hasKeyword,
                    keywordText = keywordText,
                    snrScore = snrScore
                )
            } catch (e: Exception) {
                Timber.e(e, "❌ InferenceEngine 오류 — 기본값 반환 [seq=%d]", seq)
                InferenceResult(
                    probabilities = FloatArray(7) { 0f },
                    maxProbability = 0f,
                    topClassIndex = 0,
                    hasKeyword = false,
                    keywordText = "",
                    snrScore = 0f
                )
            }
        }
}