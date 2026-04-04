// core/utils/DateUtils.kt
package com.example.rush_hz_plus.core.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    private val SHORT_FORMAT = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    /** 절대 시간: "06/01 12:34" */
    fun formatAbsolute(timestamp: Long): String {
        return SHORT_FORMAT.format(Date(timestamp))
    }

    /** 상대 시간: "2분 전", "1시간 전" 등 */
    fun formatRelative(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMillis = now - timestamp

        return when {
            diffMillis < 60_000 -> "방금 전"
            diffMillis < 3_600_000 -> {
                val minutes = (diffMillis / 60_000).toInt()
                "${minutes}분 전"
            }
            diffMillis < 86_400_000 -> {
                val hours = (diffMillis / 3_600_000).toInt()
                "${hours}시간 전"
            }
            else -> formatAbsolute(timestamp)
        }
    }
}