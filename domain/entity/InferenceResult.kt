// domain/entity/InferenceResult.kt
package com.example.rush_hz_plus.domain.entity


data class InferenceResult(
    val probabilities: FloatArray, // TFLite 출력 (6개 클래스 확률)
    val maxProbability: Float,
    val topClassIndex: Int,
    val hasKeyword: Boolean,       // Vosk 키워드 감지 여부
    val keywordText: String?,      // Vosk에서 감지된 실제 키워드 텍스트
    val snrScore: Float,           // SNR 점수 (-10 ~ +10)
    val timestamp: Long = System.currentTimeMillis()
)