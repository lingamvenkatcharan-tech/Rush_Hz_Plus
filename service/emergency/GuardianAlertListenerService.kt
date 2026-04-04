package com.example.rush_hz_plus.service.emergency

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.*
import androidx.core.app.NotificationCompat
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.domain.usecase.UserIdProvider
import com.example.rush_hz_plus.service.system.PermissionManager
import com.example.rush_hz_plus.ui.main.MainActivity
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicInteger

@AndroidEntryPoint
class GuardianAlertListenerService : Service() {

    @Inject
    lateinit var database: DatabaseReference

    @Inject
    lateinit var userIdProvider: UserIdProvider

    @Inject
    lateinit var permissionManager: PermissionManager

    // SMSHelper 주입
    @Inject
    lateinit var smsHelper: SMSHelper

    private var guardianUid: String = ""
    private var guardianAlertsRef: DatabaseReference? = null
    private var childEventListener: ChildEventListener? = null

    private val notificationIdCounter = AtomicInteger(1)

    companion object {
        private const val TAG = "GuardianAlertListener"
        private const val CHANNEL_ID = "guardian_alerts_channel"
        private const val CHANNEL_NAME = "Guardian Alerts"
        private const val FOREGROUND_NOTIFICATION_ID = 1002
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Android 14+ FGS 타입: dataSync
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                createForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        }

        guardianUid = userIdProvider.getCurrentUserId()
        if (guardianUid == "anonymous") {
            Timber.w(TAG, "익명 사용자 — GuardianAlertListenerService 중단")
            stopSelf()
            return
        }

        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (guardianUid.isEmpty() || guardianUid == "anonymous") {
            Timber.w(TAG, "guardianUid 없음 — 서비스 종료 (flags=$flags)")
            stopSelf()
            return START_NOT_STICKY
        }

        if (guardianAlertsRef == null || childEventListener == null) {
            Timber.i(TAG, "RTDB 리스너 재등록 시도 (flags=$flags)")
            startListening()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ObsoleteSdkInt")
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "보호자용 긴급 알림 수신 대기"
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rush Hz+ 보호자 모드")
            .setContentText("긴급 알림 수신 대기 중")
            .setSmallIcon(R.drawable.alarm)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun startListening() {
        guardianAlertsRef = database.child("guardian_alerts").child(guardianUid)
        childEventListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val title = snapshot.child("title").getValue(String::class.java) ?: "긴급 알림"
                    val body = snapshot.child("body").getValue(String::class.java) ?: ""

                    showEmergencyNotification(title, body)

                    snapshot.ref.child("status").setValue("delivered")
                        .addOnFailureListener { e ->
                            Timber.e(e, "GuardianAlertListener: status 업데이트 실패")
                        }
                } catch (e: Exception) {
                    Timber.e(e, "GuardianAlertListener: 알림 처리 중 오류")
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit

            override fun onCancelled(error: DatabaseError) {
                Timber.e(error.toException(), "GuardianAlertListener: DB 연결 취소됨")
                stopSelf()
            }
        }

        try {
            guardianAlertsRef?.addChildEventListener(childEventListener!!)
        } catch (e: SecurityException) {
            Timber.e(e, "RTDB 리스너 등록 실패 — RECEIVER_NOT_EXPORTED 누락 가능성")
            stopSelf()
        }
    }

    private fun stopListening() {
        childEventListener?.let { listener ->
            guardianAlertsRef?.removeEventListener(listener)
        }
        childEventListener = null
        guardianAlertsRef = null
    }

    private fun showEmergencyNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!permissionManager.hasNotificationPermission(this)) {
                Timber.w(TAG, "알림 권한 없음 — 긴급 알림 생략")
                return
            }
        }

        val emergencyChannelId = "guardian_emergency_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                emergencyChannelId,
                "긴급 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "보호자에게 전달되는 긴급 상황 알림"
                enableVibration(true)
                enableLights(true)
                lightColor = Color.RED
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("from_emergency", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationIdCounter.getAndIncrement(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, emergencyChannelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.alarm)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setLights(Color.RED, 1000, 1000)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationIdCounter.getAndIncrement(), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 핵심: 서비스 종료 시 SMS 리시버 해제
        smsHelper.unregisterBroadcastReceivers()
        stopListening()
    }
}