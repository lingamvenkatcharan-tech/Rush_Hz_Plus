package com.example.rush_hz_plus.ui.alert


import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.service.alert.FlashAlert
import com.example.rush_hz_plus.service.alert.TTSManager
import com.example.rush_hz_plus.service.alert.VibrationAlert
import com.example.rush_hz_plus.service.alert.VibrationPattern
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class FullScreenAlarmActivity : AppCompatActivity() {

    @Inject lateinit var vibrationAlert: VibrationAlert
    @Inject lateinit var flashAlert: FlashAlert
    @Inject lateinit var ttsManager: TTSManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var ttsJob: Job? = null  // 발화 제어용 Job

    companion object {
        private const val EXTRA_MESSAGE = "extra_message"
    }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Timber.d("Notification permission granted=$granted")
        }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    @SuppressLint("WakelockTimeout", "Wakelock")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_alarm)

        // 잠금화면/화면 켜기
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // WakeLock을 필드에 담아두기(섀도잉 금지)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "rush_hz_plus:FullScreenAlarm"
        ).apply { acquire(10_000L) }

        // UI
        val message = intent.getStringExtra(EXTRA_MESSAGE)
            ?: getString(R.string.default_emergency_message)
        findViewById<TextView>(R.id.text_message).text = message
        findViewById<ImageButton>(R.id.btn_close).setOnClickListener {
            finish() // 🔸 먼저 finish → lifecycle 내려가며 onStop/onDestroy에서 정리
        }

        // Android 13+ 알림 권한
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 하드웨어 경고(자체적으로 idempotent 하도록 구현됨)
        runCatching {
            vibrationAlert.vibratePattern(VibrationPattern.HIGH)
        }.onFailure { Timber.e(it, "Vibration start failed") }

        runCatching {
            flashAlert.blinkContinuous(interval = 150L)
        }.onFailure { Timber.e(it, "Flash start failed") }

        // 배경 점멸 애니메이션
        findViewById<View>(R.id.flash_background).apply {
            startAnimation(AlphaAnimation(0.2f, 1.0f).apply {
                duration = 400
                repeatMode = AlphaAnimation.REVERSE
                repeatCount = AlphaAnimation.INFINITE
            })
        }

        // === 🔥 TTS: L3 긴급 알림 (fallback 포함) ===
        // 📌 기술 문서 §4.3.2 & §5.4 반영:
        //   - TTS 실패 시 진동/플래시 강화 (Z3 → Z4) + UI 텍스트 강조
        //   - 사용자에게 "음성 없음"을 인지시키되, 책임 전가하지 않음 (자동 fallback)
        ttsJob = lifecycleScope.launch {
            ttsManager.speakL3Emergency(
                text = message,
                onFallback = {
                    // 📢 fallback 1: 진동 강화 (Z3 → Z4)
                    vibrationAlert.vibratePattern(VibrationPattern.HIGHEST)

                    // 📢 fallback 2: 플래시 속도 ↑ + 제한 회수 → 명확한 시각 신호
                    findViewById<View>(R.id.flash_background).apply {
                        clearAnimation()
                        startAnimation(AlphaAnimation(0f, 1.0f).apply {
                            duration = 180
                            repeatMode = AlphaAnimation.REVERSE
                            repeatCount = 6 // 7회 깜빡임 후 멈춤 → 사용자 인지 유도
                            setAnimationListener(object : Animation.AnimationListener {
                                override fun onAnimationEnd(animation: Animation?) {
                                    // 📢 fallback 3: UI 텍스트 강조
                                    findViewById<TextView>(R.id.text_message).text =
                                        "🚨 $message (음성 출력 지연됨)"
                                }
                                override fun onAnimationStart(animation: Animation?) {}
                                override fun onAnimationRepeat(animation: Animation?) {}
                            })
                        })
                    }

                    Timber.d("TTS fallback executed: vibration↑ + flash↑ + UI update")
                }
            )
            Timber.d("TTS L3 emergency requested: '$message'")
        }
    }

    private fun stopAllAlerts() {
        // 순서: (1) 발화 job 취소 → (2) TTS stop → (3) 나머지
        try { ttsJob?.cancel() } catch (_: Exception) {}
        try { ttsManager.stop() } catch (e: Exception) { Timber.w(e) }
        try { vibrationAlert.stopVibration() } catch (e: Exception) { Timber.w(e) }
        try { flashAlert.stopBlinking() } catch (e: Exception) { Timber.w(e) }
        try { findViewById<View>(R.id.flash_background).clearAnimation() } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Timber.w(e, "WakeLock release failed")
        } finally {
            wakeLock = null
        }
    }

    override fun onStop() {
        super.onStop()
        // 사용자가 닫기를 누르거나, 화면이 내려갈 때 즉시 중지
        // (onDestroy까지 기다리지 않음 — 경합을 줄이기 위해 빠른 정리)
        stopAllAlerts()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        // stopAllAlerts()는 onStop에서 이미 실행됨. 여기서 중복 호출하지 않음.
    }
}
