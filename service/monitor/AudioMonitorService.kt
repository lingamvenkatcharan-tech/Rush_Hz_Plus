package com.example.rush_hz_plus.service.monitor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.core.utils.AccessibilityUtil
import com.example.rush_hz_plus.domain.usecase.ProcessAudioUseCase
import com.example.rush_hz_plus.service.alert.TTSManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.min

@AndroidEntryPoint
class AudioMonitorService : Service() {

    @Inject
    lateinit var processAudioUseCase: ProcessAudioUseCase
    lateinit var ttsManager : TTSManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var processJob: Job? = null

    // AudioRecord 관련 상태
    @Volatile private var audioRecord: AudioRecord? = null
    private val SAMPLE_RATE = 16_000
    private val CH_IN = AudioFormat.CHANNEL_IN_MONO
    private val ENC = AudioFormat.ENCODING_PCM_16BIT
    private val ONE_SEC_SAMPLES = 16_000

    private val audioChannel = Channel<ShortArray>(capacity = 4)

    companion object {
        const val CHANNEL_ID = "RushHzPlusForegroundChannel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "AudioMonitorService"
    }

    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).i("✅ onCreate()")
        createNotificationChannel()

        // ⚠️ isEligibleToRun() 제거: 시작 책임은 UI 계층이 담당
        // 서비스는 시작된 이후의 오디오 처리에만 집중
    }

    @SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.tag(TAG).i("✅ onStartCommand() — FGS 승격 및 파이프라인 시작")

        // 권한 체크만 수행 (시작 책임은 UI 계층이지만, 안전을 위해 재확인)
        if (!hasRequiredPermissions()) {
            Timber.tag(TAG).e("❌ 필수 권한 부족 → 서비스 중지")
            stopSelfSafely()
            return START_NOT_STICKY
        }

        try {
            // Foreground 승격 (Android 14 microphone 타입 대응)
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // 녹음 파이프라인 시작
            serviceScope.launch {
                try {
                    Timber.tag(TAG).i("✅ 권한 OK → AudioRecord init & loops start")
                    reinitAudioRecord(force = true)
                    startAudioCaptureLoop()
                    startAudioProcessingLoop()

                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "❌ 서비스 초기화 실패")
                    stopSelfSafely()
                }
            }

        } catch (se: SecurityException) {
            Timber.tag(TAG).e(se, "❌ SecurityException — FGS 승격 실패")
            stopSelfSafely()
            return START_NOT_STICKY
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ onStartCommand 예외 발생")
            stopSelfSafely()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    // =========================
    // 권한 체크 (필수 권한만 확인)
    // =========================
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun hasRequiredPermissions(): Boolean {
        val hasRecordAudio = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        // Android 13 (TIRAMISU, API 33)부터 FOREGROUND_SERVICE_MICROPHONE 필요
        val hasFgsMic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        Timber.tag(TAG).i("권한상태: RECORD_AUDIO=$hasRecordAudio, FGS_MIC(SDK>=33)=$hasFgsMic")
        return hasRecordAudio && hasFgsMic
    }

    // =========================
    // AudioRecord 초기화/재시작
    // =========================
    private fun buildBestAudioRecord(): AudioRecord? {
        // minBuffer 계산
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, CH_IN, ENC).coerceAtLeast(ONE_SEC_SAMPLES * 2)
        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(ENC)
            .setChannelMask(CH_IN)
            .build()

        // 접근성 여부에 따른 오디오 소스 선택 유지 (기능적 차이)
        val sources = if (isAccessibilityServiceEnabled()) {
            intArrayOf(MediaRecorder.AudioSource.MIC)
        } else {
            intArrayOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
            )
        }

        for (src in sources) {
            try {
                val rec = AudioRecord.Builder()
                    .setAudioSource(src)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(min)
                    .build()

                Timber.tag(TAG).i("✅ AudioRecord build: src=$src state=${rec.state} minBuf=$min")
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    return rec
                } else {
                    Timber.tag(TAG).w("AudioRecord 상태 비정상(state=${rec.state}) → release")
                    runCatching { rec.release() }
                }
            } catch (se: SecurityException) {
                Timber.tag(TAG).e(se, "SecurityException: src=$src")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "AudioRecord build 실패: src=$src")
            }
        }
        return null
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return AccessibilityUtil.isAccessibilityServiceEnabled(this)
    }

    @Synchronized
    private fun reinitAudioRecord(force: Boolean = false) {
        if (!force && audioRecord?.state == AudioRecord.STATE_INITIALIZED) return

        // 이전 객체 안전 종료
        runCatching {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
                it.release()
                Timber.tag(TAG).i("✅ 이전 AudioRecord 정리 완료")
            }
        }.onFailure { Timber.tag(TAG).w(it, "old AudioRecord release 실패") }

        audioRecord = null

        // 새 객체 준비
        val rec = buildBestAudioRecord()
        if (rec == null) {
            Timber.tag(TAG).e("❌ AudioRecord 생성 실패 → 재시도 루프에서 재도전")
            return
        }
        audioRecord = rec
        try {
            rec.startRecording()
            Timber.tag(TAG).i("AudioRecord started: state=${rec.state}, recState=${rec.recordingState}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "startRecording 실패 → release")
            runCatching { rec.release() }
            audioRecord = null
        }
    }

    // =========================
    // 캡처 루프
    // =========================
    private fun startAudioCaptureLoop() {
        captureJob?.cancel()
        captureJob = serviceScope.launch {
            Timber.tag(TAG).i("startAudioCaptureLoop() 시작 (thread=${Thread.currentThread().name})")
            val temp = ShortArray(1600) // 100ms씩
            val oneSec = ShortArray(ONE_SEC_SAMPLES)
            var pos = 0
            var consecutiveSilent = 0
            var frameSeq = 0L
            var reinitBackoffMs = 200L

            while (isActive) {
                val rec = audioRecord
                if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                    Timber.tag(TAG).w("AudioRecord null/미초기화 → ${reinitBackoffMs}ms 대기 후 재시도")
                    delay(reinitBackoffMs)
                    reinitBackoffMs = (reinitBackoffMs * 2).coerceAtMost(4_000L)
                    reinitAudioRecord(force = true)
                    continue
                } else {
                    reinitBackoffMs = 200L
                }

                val read = try {
                    rec.read(temp, 0, temp.size)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "❌ read 실패 → AudioRecord 재초기화")
                    reinitAudioRecord(force = true)
                    continue
                }

                if (read <= 0) {
                    Timber.tag(TAG).w("read=$read → 50ms 대기 후 재시도")
                    delay(50)
                    continue
                }

                // 중첩 버퍼링
                val copy = min(read, ONE_SEC_SAMPLES - pos)
                System.arraycopy(temp, 0, oneSec, pos, copy)
                pos += copy

                if (pos >= ONE_SEC_SAMPLES) {
                    // 1초 프레임 완성 → 간단한 RMS로 무음 판단
                    var sumSq = 0.0
                    for (v in oneSec) sumSq += (v * v).toDouble()
                    val rms = kotlin.math.sqrt(sumSq / ONE_SEC_SAMPLES)
                    val silent = rms < 100.0
                    if (silent) consecutiveSilent++ else consecutiveSilent = 0

                    frameSeq++

                    val offered = audioChannel.trySend(oneSec.clone()).isSuccess
                    Timber.tag(TAG).i(
                        "✅ PIPE(frame=%d) → enqueued=%s, rms=%.1f, silent=%s",
                        frameSeq,
                        offered,
                        rms,
                        silent
                    )

                    if (consecutiveSilent >= 60) { // ~1분 무음
                        Timber.tag(TAG).w("장시간 무음 → AudioRecord 재초기화")
                        reinitAudioRecord(force = true)
                        consecutiveSilent = 0
                    }
                    pos = 0
                }
            }
        }
    }

    // =========================
    // 처리 루프
    // =========================
    private fun startAudioProcessingLoop() {
        processJob?.cancel()
        processJob = serviceScope.launch {
            Timber.tag(TAG).i("✅ startAudioProcessingLoop() 시작 (thread=${Thread.currentThread().name})")
            var seq = 0L
            for (buf in audioChannel) {
                seq++
                Timber.tag(TAG).i("✅ PROCESS in → frame=%d, bufLen=%d", seq, buf.size)
                runCatching {
                    processAudioUseCase.execute(buf)
                }.onSuccess {
                    Timber.tag(TAG).i("✅ PROCESS out ← frame=%d OK", seq)
                }.onFailure {
                    Timber.tag(TAG).e(it, "❌ ProcessAudioUseCase 실패 — frame=%d DROPPED", seq)
                }
            }
            Timber.tag(TAG).w("audioChannel closed — processing loop end")
        }
    }

    // -------------------------
    // 알림
    // -------------------------
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hz+ Foreground Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "위험 소리 감지 서비스 실행 중"
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hz+")
            .setContentText("위험 소리 감지 중…")
            .setSmallIcon(R.drawable.alarm)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    // -------------------------
    // 종료/정리
    // -------------------------
    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).i("🛑 onDestroy() — 파이프라인 종료")
        captureJob?.cancel()
        processJob?.cancel()
        serviceScope.cancel()
        runCatching {
            audioChannel.close()
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
                it.release()
            }
        }
        audioRecord = null
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}