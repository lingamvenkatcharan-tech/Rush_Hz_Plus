// core/utils/Logger.kt
package com.example.rush_hz_plus.core.utils

import android.util.Log
import com.google.firebase.crashlytics.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber
import timber.log.Timber.DebugTree

object Logger {

    // 개발 환경에서만 DebugTree 사용
    private val isDebugBuild = BuildConfig.DEBUG

    // 로그 출력 (Timber 기반)
    fun d(tag: String, message: String) {
        if (isDebugBuild) Timber.d("$tag: $message")
    }

    fun i(tag: String, message: String) {
        if (isDebugBuild) Timber.i("$tag: $message")
        else Log.i(tag, message) // Release 모드에서도 기본 로그 남김
    }

    fun w(tag: String, message: String) {
        if (isDebugBuild) Timber.w("$tag: $message")
        else Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (isDebugBuild) {
            Timber.e(throwable, "$tag: $message")
        } else {
            Timber.tag(tag).e(throwable, message)
        }

        // 💡 Crashlytics에 예외 기록 (모든 에러 및 예외 자동 기록)
        if (throwable != null) {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } else {
            FirebaseCrashlytics.getInstance().log("$tag: $message")
        }
    }

    // 🔥 핵심: 개발자에게 명확한 경고 제공
    fun logCrash(tag: String, message: String, exception: Throwable) {
        e(tag, message, exception)
    }

    // 🚨 특별한 위험 상황 기록 (예: 긴급 알림 전송 실패 등)
    fun logCriticalEvent(message: String) {
        if (isDebugBuild) {
            Timber.e("CRITICAL: $message")
        }
        FirebaseCrashlytics.getInstance().log("CRITICAL: $message")
    }

    // 🧪 개발용: 현재 트리 수 확인
    fun isInitialized(): Boolean = Timber.treeCount > 0
}