// service/emergency/EmergencyCallService.kt
package com.example.rush_hz_plus.service.emergency

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.rush_hz_plus.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class EmergencyCallService : Service() {

    companion object {
        const val ACTION_START = "com.example.rush_hz_plus.action.START_CALLS"
        const val EXTRA_PHONE_NUMBERS = "phone_numbers"
        const val FOREGROUND_CALL_ID = 1003

        fun start(context: Context, phones: List<String>) {
            if (phones.isEmpty()) return
            val intent = Intent(context, EmergencyCallService::class.java).apply {
                action = ACTION_START
                putStringArrayListExtra(EXTRA_PHONE_NUMBERS, ArrayList(phones))
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) return START_NOT_STICKY

        val phones = intent.getStringArrayListExtra(EXTRA_PHONE_NUMBERS) ?: emptyList()
        if (phones.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 포그라운드 알림 (단순화 — NotificationHelper 없이 직접 생성)
        val notification = createForegroundNotification()
        startForeground(FOREGROUND_CALL_ID, notification)

        // 자동 전화 (0.8초 간격)
        CoroutineScope(Dispatchers.Main).launch {
            for ((i, phone) in phones.withIndex()) {
                if (i > 0) delay(800L)

                try {
                    val callIntent = Intent(Intent.ACTION_CALL).apply {
                        this.data = Uri.parse("tel:$phone")
                        this.flags = Intent.FLAG_ACTIVITY_NEW_TASK  // 수정: START_FLAG_REDELIVERY → FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(callIntent)
                    Timber.i("📞 자동 전화 발신: $phone")
                    recordCallAttempt(phone, success = true)
                } catch (e: Exception) {
                    Timber.e(e, "📞 전화 실패: $phone")
                    recordCallAttempt(phone, success = false)
                }
            }

            delay(5000)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun createForegroundNotification(): Notification {
        // 🔹 1. 채널 생성 — IMPORTANCE_HIGH 필수
        createNotificationChannel()

        return NotificationCompat.Builder(this, "call_service_channel")
            .setContentTitle("Hz+ 긴급 전화")
            .setContentText("보호자에게 자동 전화 중...")
            .setSmallIcon(R.drawable.alarm)
            .setOngoing(true)          // 🔹 필수: FGS는 ongoing이어야 함
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 🔹 HIGH 이상
            .setCategory(Notification.CATEGORY_CALL)       // 🔹 PHONE_CALL 타입과 일치
            // 🔹 필수: Android 14+는 최소 1개 액션 필요
            .addAction(
                R.drawable.ic_cellphone,
                "통화 중단",
                createCancelPendingIntent()
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "call_service_channel",
                "Hz+ 긴급 통화",
                NotificationManager.IMPORTANCE_HIGH // 🔹 LOW → HIGH
            ).apply {
                description = "보호자 자동 통화 서비스"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.createNotificationChannel(channel)
        }
    }

    private fun createCancelPendingIntent(): PendingIntent {
        return PendingIntent.getService(
            this,
            0,
            Intent(this, EmergencyCallService::class.java).apply {
                action = "ACTION_CANCEL"
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun recordCallAttempt(phone: String, success: Boolean) {
        // 실제론 detectionRepository 사용
        Timber.d("CallCheck | $phone → ${if (success) "OK" else "FAIL"}")
    }
}