// domain/score/ClassScorer.kt
package com.example.rush_hz_plus.domain.score

object ClassScorer {

    // 클래스 메타데이터 통합
    data class HazardClass(
        val label: String,
        val emoji: String,
        val score: Int
    )

    // 유일한 진실 원천
    private val HAZARD_CLASSES = listOf(
        HazardClass("SAFE", "✅", 0),  // 인덱스 0
        HazardClass("Siren", "🚨", 30),                    // 사이렌: 매우 위험 (소방/경찰)
        HazardClass("Gunshot, gunfire", "🔫", 30),        // 총성: 극도 위험
        HazardClass("Glass", "🫗", 25),                   // 유리 파손: 매우 위험 (침입/사고)
        HazardClass("Screaming", "😱", 25),               // 비명: 매우 위험 (긴급 상황)
        HazardClass("Crying, sobbing", "😢", 5),          // 울음: 매우 낮은 위험
        HazardClass("Vehicle horn, car horn, honking", "🚗", 10), // 경적: 낮은 위험 (일상 소음)
    )

    // 인덱스 기반 접근
    fun getLabel(classIndex: Int): String =
        HAZARD_CLASSES.getOrNull(classIndex)?.label ?: "Unknown($classIndex)"

    fun getEmoji(classIndex: Int): String =
        HAZARD_CLASSES.getOrNull(classIndex)?.emoji ?: "❓"

    fun getScore(classIndex: Int): Int =
        HAZARD_CLASSES.getOrNull(classIndex)?.score ?: 5
}