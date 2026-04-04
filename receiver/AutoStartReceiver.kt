package com.example.rush_hz_plus.receiver

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.core.utils.AccessibilityUtil
import com.example.rush_hz_plus.ui.main.MainActivity
import timber.log.Timber

/**
 * 부팅/앱 업데이트 시 사용자 동작을 유도하는 알림 표시 리시버
 *
 * ⚠️ 주의: Android 14+에서는 백그라운드 컴포넌트에서 FGS 시작 금지
 * - FGS 시작 시도 완전 제거
 * - "Hz+ 시작" 알림으로 사용자 동작 유도
 */
class AutoStartReceiver : BroadcastReceiver() {

    companion object {
        private const val NOTIFICATION_ID = 999
        private const val CHANNEL_ID = "hzplus_startup_channel"
        private const val TAG = "AutoStart"
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            Timber.tag(TAG).i("수신됨: ${intent.action}")

            // 디버깅 로그 유지 (상태 확인용)
            val isAccessibilityEnabled = AccessibilityUtil.isAccessibilityServiceEnabled(context)
            val hasRecordAudio = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            val hasFgsMic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
                ) == PackageManager.PERMISSION_GRANTED
            } else true

            Timber.tag(TAG).i(
                "%snull", "상태 - 접근성: $isAccessibilityEnabled, " +
                        "RECORD_AUDIO: $hasRecordAudio, "
            )

            // FGS 시작 시도 완전 제거
            // context.startForegroundService() 호출 금지

            // 사용자 동작 유도를 위한 알림 표시
            showStartMonitoringNotification(context)
        }
    }

    /**
     * Hz+ 시작을 유도하는 알림 표시
     *
     * - 접근성 활성화 여부와 무관하게 동일한 알림 표시
     * - 알림 클릭 시 MainActivity로 이동하여 사용자 제어권 제공
     */
    private fun showStartMonitoringNotification(context: Context) {
        // 알림 채널 생성 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Hz+ 시작 알림",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Hz+ 위험 소리 감지 서비스를 시작하려면 탭하세요."
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager)
                ?.createNotificationChannel(channel)
        }

        // MainActivity로 이동하는 PendingIntent (사용자 동작 유도)
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("FROM_AUTO_START", true) // 선택적: MainActivity에서 특별 처리 가능
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 알림 생성
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.alarm)
            .setContentTitle("Hz+ 시작하기")
            .setContentText("위험 소리 감지 서비스를 시작하려면 탭하세요.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // 알림 표시
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        Timber.tag(TAG).i("'Hz+ 시작' 알림 표시 완료")
    }
}