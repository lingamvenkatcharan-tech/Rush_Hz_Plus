// domain/score/HazardScore.kt
package com.example.rush_hz_plus.domain.score

// 개선: HazardScore를 sealed class로 확장 -> 'AlertManager → 이벤트 로깅 DB 저장'
/**
 * 위험 감지 결과 및 점수 정보를 담는 데이터 클래스
 */
data class HazardScore(
    val rawScore: Float,          // 원점수: -10 ~ 160
    val normalizedScore: Float,   // 정규화 점수: 0 ~ 100
    val level: String,            // "SAFE", "L1", "L2", "L3"
    val isEmergency: Boolean      // L3인 경우 true → 긴급 조치 필요
) {
    companion object {
        const val LEVEL_SAFE = "SAFE"
        const val LEVEL_L1 = "L1"
        const val LEVEL_L2 = "L2"
        const val LEVEL_L3 = "L3"

        /**
         * 앱 시작 시 또는 초기 상태용 안전한 기본값 반환
         */
        fun safeDefault(): HazardScore = HazardScore(
            rawScore = 0f,
            normalizedScore = 0f,
            level = LEVEL_SAFE,
            isEmergency = false
        )
    }

    // UI 표시용 텍스트 변환
    fun toDisplayText(): String {
        return when (level) {
            LEVEL_L3 -> "⚠️ 매우 위험"
            LEVEL_L2 -> "❗ 위험"
            LEVEL_L1 -> "ℹ️ 주의"
            else -> "안전"
        }
    }

    // 편의 함수
    fun isSafe() = level == LEVEL_SAFE
    fun isL1() = level == LEVEL_L1
    fun isL2() = level == LEVEL_L2
    fun isL3() = level == LEVEL_L3
}