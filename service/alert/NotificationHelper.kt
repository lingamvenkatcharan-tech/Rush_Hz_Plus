// service/alert/NotificationHelper.kt
package com.example.rush_hz_plus.service.alert


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.domain.score.HazardScore
import com.example.rush_hz_plus.service.system.PermissionManager
import com.example.rush_hz_plus.ui.main.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NotificationHelper"

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionManager: PermissionManager
) {

    private val CHANNEL_ID_CRITICAL = "risk_alert_channel_critical"
    private val CHANNEL_ID_HIGH = "risk_alert_channel_high"
    private val CHANNEL_ID_MEDIUM = "risk_alert_channel_medium"

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // L3: 매우 위험 (고우선순위 알림, FullScreenIntent 제거)
            val criticalChannel = NotificationChannel(
                CHANNEL_ID_CRITICAL,
                "매우 위험 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "매우 위험한 상황 알림"
                enableLights(true)
                enableVibration(true)
                // lockscreenVisibility는 유지 (잠금 화면에 표시)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // L2: 위험
            val highChannel = NotificationChannel(
                CHANNEL_ID_HIGH,
                "위험 알림",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "위험 상황 알림"
                enableLights(true)
                enableVibration(true)
            }

            // L1: 주의
            val mediumChannel = NotificationChannel(
                CHANNEL_ID_MEDIUM,
                "주의 알림",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "주의 상황 알림"
                enableLights(false)
                enableVibration(false)
            }

            manager.createNotificationChannel(criticalChannel)
            manager.createNotificationChannel(highChannel)
            manager.createNotificationChannel(mediumChannel)
        }
    }

    /**
     * 레벨별 알림 표시 (FullScreenIntent 제거)
     */
    fun showAlert(score: HazardScore, keyword: String) {
        val (title, body) = when (score.level) {
            HazardScore.LEVEL_L3 -> Pair("🆘 긴급 알림", "즉시 확인이 필요합니다. 키워드: $keyword")
            HazardScore.LEVEL_L2 -> Pair("🚨 위험 알림", "위험 상황이 감지되었습니다.")
            HazardScore.LEVEL_L1 -> Pair("⚠️ 주의 알림", "주의가 필요합니다.")
            else -> return
        }

        val notification = when (score.level) {
            HazardScore.LEVEL_L3 -> createCriticalNotification(title, body)
            HazardScore.LEVEL_L2 -> createHighNotification(title, body)
            HazardScore.LEVEL_L1 -> createMediumNotification(title, body)
            else -> return
        }

        notify(notification)
    }

    private fun createCriticalNotification(title: String, body: String): Notification {
        // MainActivity로 이동하는 PendingIntent (FullScreenIntent 제거)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // DashboardFragment로 이동하도록 추가 데이터 전달 가능
            putExtra("show_emergency_dialog", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(), // 고유 ID로 변경
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_CRITICAL)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // FullScreenIntent 제거
            .setContentIntent(pendingIntent) // 일반 클릭 인텐트
            .setAutoCancel(true)
            .build()
    }

    private fun createHighNotification(title: String, body: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_HIGH)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun createMediumNotification(title: String, body: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_MEDIUM)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.alarm)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }


    private fun notify(notification: Notification) {
        // Android 13+ 알림 권한 체크
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!permissionManager.hasNotificationPermission(context)) {
                Timber.w(TAG, "알림 권한 없음 — 알림 표시 생략")
                return
            }
        }

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            Timber.e(TAG, "알림 표시 중 보안 예외 발생", e)
        } catch (e: Exception) {
            Timber.e(TAG, "알림 표시 중 일반 예외 발생", e)
        }
    }

    /**
     * 포그라운드 서비스용 알림
     */
    fun buildForegroundServiceNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID_MEDIUM)
            .setContentTitle("실시간 위험 감지 서비스 실행 중")
            .setContentText("주변 소리를 모니터링하고 있습니다...")
            .setSmallIcon(R.drawable.alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun buildEmergencyCallNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID_MEDIUM)
            .setContentTitle("자동 전화 알림 중")
            .setContentText("보호자에게 긴급 전화 연결 시도 중...")
            .setSmallIcon(R.drawable.alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}