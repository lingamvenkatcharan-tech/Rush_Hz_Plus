package com.example.rush_hz_plus.service.system

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 권한 상태 확인 및 요청 전용 매니저
 *
 * ⚠️ 주의: Android 14+ 정책에 완전히 준수
 * - 불필요한 권한(ACCESS_FINE_LOCATION) 제거
 * - FOREGROUND_SERVICE_MICROPHONE은 Android 13(TIRAMISU, API 33)부터 필수
 * - FGS 시작 가능성 판단 로직 제거 (사용자 직접 동작만 허용)
 */
@Singleton
class PermissionManager @Inject constructor() {

    companion object {
        const val REQUEST_CODE_PERMISSIONS = 1001

        /**
         * Hz+ 앱에 필요한 필수 권한 목록
         *
         * 📌 권한 정책 정합성:
         * - RECORD_AUDIO: 위험 소리 감지 필수
         * - FOREGROUND_SERVICE_MICROPHONE: Android 13+(API 33)부터 마이크 FGS 필수
         * - POST_NOTIFICATIONS: Android 13+(API 33)부터 알림 필수
         */
        val REQUIRED_PERMISSIONS = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.CALL_PHONE)

            // Android 14+(API 34)부터 FGS 권한 필요
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC) // FGS 타입 권한
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    // ---------- 기본 has* ----------

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasMicrophonePermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.RECORD_AUDIO)

    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        } else true

    fun hasVibrationCapability(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.hasVibrator()
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).hasVibrator()
        }
    }

    fun hasAllPermissions(context: Context): Boolean =
        REQUIRED_PERMISSIONS.all { hasPermission(context, it) }

    /** 디버깅용 */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun logPermissionStatus(context: Context) {
        val permissions = mapOf(
            "RECORD_AUDIO" to hasMicrophonePermission(context),
            "POST_NOTIFICATIONS" to hasNotificationPermission(context)
        )

        // Android 13+인 경우에만 FGS_MIC 권한 상태 표시
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.plus(
                "FOREGROUND_SERVICE_MICROPHONE" to
                        hasPermission(context, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
            ).forEach { (k, v) ->
                Timber.tag("🔐 권한 상태").i("%s = %s", k, if (v) "허용됨" else "거부됨")
            }
        } else {
            permissions.forEach { (k, v) ->
                Timber.tag("🔐 권한 상태").i("%s = %s", k, if (v) "허용됨" else "거부됨")
            }
        }
    }

    // ---------- Activity 권한 요청 로직 ----------

    /**
     * Activity 단에서 아직 허용되지 않은 권한들을 요청.
     * Android 13+ POST_NOTIFICATIONS도 함께 포함.
     */
    fun requestMissingPermissions(activity: Activity) {
        val toRequest = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, toRequest, REQUEST_CODE_PERMISSIONS)
        }
    }

    /**
     * requestPermissions() 결과 처리.
     * true = 모두 허용됨, false = 일부 거부됨
     */
    fun handlePermissionResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode != REQUEST_CODE_PERMISSIONS) return false
        val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        if (allGranted) {
            Timber.tag("PermissionManager").i("✅ 모든 권한이 허용되었습니다.")
        } else {
            Timber.tag("PermissionManager").w("⚠️ 일부 권한이 거부되었습니다.")
        }

        return allGranted
    }
}