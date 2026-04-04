package com.example.rush_hz_plus.service.alert

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.media.AudioAttributes
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class TTSManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TTSManager"
        private const val SYSTEM_UTTERANCE_ID = "rush_hz_alert"
        private const val DEFAULT_SPEECH_RATE = 0.9f
        private const val DEFAULT_PITCH = 1.1f
    }

    // -----------------------------
    // Internal state
    // -----------------------------
    private var tts: TextToSpeech? = null

    private val initLock = Mutex()
    private val isInitializedFlag = AtomicBoolean(false)
    private val isSpeakingFlag = AtomicBoolean(false)

    // Initialization completion
    @Volatile private var readyDeferred: CompletableDeferred<Unit> = CompletableDeferred()

    // Dedicated scope (IO for non-UI work, switch to Main only when touching TTS)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    // -----------------------------
    // Initialization
    // -----------------------------

    /**
     * Lazily ensure TTS is initialized.
     * Safe to call multiple times; only first call performs real initialization.
     */
    private suspend fun ensureInitialized() {
        if (isInitializedFlag.get()) return
        initLock.withLock {
            if (isInitializedFlag.get()) return
            withContext(Dispatchers.Main) {
                try {
                    if (tts == null) {
                        tts = TextToSpeech(context, this@TTSManager)
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "TTS 생성 실패")
                    // 재시도 대비: 초기화 실패 시 새로운 readyDeferred를 유지
                }
            }
        }
    }

    /**
     * Await readiness with timeout. Returns true if ready, false on timeout/failure.
     */
    suspend fun awaitReady(timeoutMs: Long = 2_000L): Boolean {
        if (isInitializedFlag.get()) return true
        // Kick off init if not started
        ensureInitialized()
        return withTimeoutOrNull(timeoutMs) {
            try {
                readyDeferred.await()
                true
            } catch (e: CancellationException) {
                false
            }
        } ?: false
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Timber.tag(TAG).e("TTS 초기화 실패(status=$status)")
            completeInit(false)
            return
        }

        val ok = try {
            configureEngine()
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "TTS 엔진 구성 실패")
            false
        }

        completeInit(ok)
    }

    private fun completeInit(success: Boolean) {
        if (success) {
            isInitializedFlag.set(true)
            if (!readyDeferred.isCompleted) readyDeferred.complete(Unit)
            Timber.tag(TAG).d("TTS 초기화 성공")
        } else {
            // 초기화 실패 시 새 deferred 유지(다음 ensureInitialized() 호출 때 재시도 가능)
            if (!readyDeferred.isCompleted) readyDeferred.completeExceptionally(IllegalStateException("TTS init failed"))
        }
    }

    private fun configureEngine() {
        val engine = tts ?: throw IllegalStateException("TTS is null during configureEngine")

        // 언어 설정
        // 🔹 수정: Locale.KOREA → ko-KR 태그 — ism-local 자동 선택 회피
        val result = engine.setLanguage(Locale.forLanguageTag("ko-KR"))
        if (result != TextToSpeech.LANG_AVAILABLE && result != TextToSpeech.LANG_COUNTRY_AVAILABLE) {
            Timber.tag(TAG).w("TTS 한국어 설정 실패(result=$result) — 기본 언어로 진행")
        }

        // 속도/피치
        engine.setSpeechRate(DEFAULT_SPEECH_RATE)
        engine.setPitch(DEFAULT_PITCH)

        @SuppressLint("ObsoleteSdkInt")
        // 오디오 어트리뷰트: 경보/안내 용도 선호
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM) // 🔹 변경: ASSISTANCE_SONIFICATION → ALARM
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            engine.setAudioAttributes(attrs)
        }

        // 음성 선택(가능하면 여성/표준 우선)
        selectPreferredKoreanVoice(engine)

        // 발화 리스너
        setupUtteranceListener(engine)
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun selectPreferredKoreanVoice(engine: TextToSpeech) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return

        // 🔹 1. "ism-local" 제외 — 음성 무출력 엔진 배제
        val voices = engine.voices?.filter {
            it.locale == Locale.forLanguageTag("ko-KR") &&
                    !it.name.contains("ism-local", ignoreCase = true)
        } ?: return

        // 🔹 2. OEM 우선: Samsung TTS → Google TTS 순
        val preferred = voices.firstOrNull {
            val n = it.name.lowercase(Locale.ROOT)
            // 삼성: "samsung", "sm-tts"
            n.contains("samsung") || n.contains("sm-tts") ||
                    // 구글: "google", "standard", "female"
                    n.contains("google") || n.contains("standard") || n.contains("female")
        } ?: voices.firstOrNull() ?: return

        engine.voice = preferred
        Timber.i("✅ TTS 음성 선택: ${preferred.name} (locale=${preferred.locale})")
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setupUtteranceListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeakingFlag.set(true)
                Timber.tag(TAG).d("TTS 시작: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                isSpeakingFlag.set(false)
                Timber.tag(TAG).d("TTS 완료: $utteranceId")
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeakingFlag.set(false)
                Timber.tag(TAG).e("TTS 오류: $utteranceId")
            }
        })
    }

    // -----------------------------
    // Public API (non-suspend for callers’ convenience)
    // 내부에서 필요한 만큼 suspend 보장
    // -----------------------------

    /**
     * 시스템 경보: 기존 발화 중단 후 즉시 재생(QUEUE_FLUSH).
     */
    fun speakSystemAlert(text: String) {
        if (text.isBlank()) return
        scope.launch {
            if (!awaitReady()) return@launch
            speakInternal(text, queueMode = TextToSpeech.QUEUE_FLUSH, utteranceId = SYSTEM_UTTERANCE_ID)
        }
    }

    /**
     * L3 긴급 알림 전용 TTS: 최대 1.5초 대기 후 재시도 없이 fallback 실행.
     * - awaitReady(1500ms)
     * - 실패 시: (1) 강제 speak 시도 (2) onFallback() 즉시 호출
     *
     * ⚠ 주의: 이 메서드는 ONLY 긴급 상황(L3)에서 사용. 일반 메시지에는 speakSystemAlert() 권장.
     */
    fun speakL3Emergency(
        text: String,
        onFallback: suspend () -> Unit = {}
    ) {
        if (text.isBlank()) return

        scope.launch {
            // 1.5초 내 초기화 기다림
            val ready = awaitReady(timeoutMs = 1500L)
            if (ready) {
                speakInternal(text, TextToSpeech.QUEUE_FLUSH, SYSTEM_UTTERANCE_ID)
                Timber.d("✅ TTS L3 emergency speak — ready & sent")
            } else {
                Timber.w("⚠️ TTS not ready in 1.5s — fallback to haptics/visual")
                // (1) 마지막 희망: 강제 speak (일부 기기선 엔진이 이미 준비됨)
                try {
                    withContext(Dispatchers.Main) {
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, SYSTEM_UTTERANCE_ID)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Fallback TTS force-call failed")
                }
                // (2) fallback 실행 — 반드시 호출
                onFallback()
            }
        }
    }

    /**
     * 커스텀 메시지: 기존 발화 유지 + 뒤에 이어 붙이기(QUEUE_ADD).
     */
    fun speakCustomMessage(text: String) {
        if (text.isBlank()) return
        scope.launch {
            if (!awaitReady()) return@launch
            speakInternal(text, queueMode = TextToSpeech.QUEUE_ADD, utteranceId = "custom_${System.currentTimeMillis()}")
        }
    }

    /**
     * 외부에서 간단히 상태 확인할 수 있도록 유지.
     */
    fun isInitialized(): Boolean = isInitializedFlag.get()
    fun isSpeaking(): Boolean = isSpeakingFlag.get()

    /**
     * 즉시 발화 중지.
     */
    fun stop() {
        try { tts?.stop() } catch (_: Exception) {}
        isSpeakingFlag.set(false)
    }

    /**
     * 매니저 완전 종료(앱 종료/서비스 정리 등)
     * - 스코프 취소, TTS 엔진 종료, 상태 초기화
     */
    fun shutdown() {
        try { scope.cancel() } catch (_: Exception) {}
        try { tts?.stop() } catch (_: Exception) {}
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null
        isInitializedFlag.set(false)
        isSpeakingFlag.set(false)
        // 다음 초기화를 위해 새로운 Deferred 준비
        readyDeferred = CompletableDeferred()
        Timber.tag(TAG).d("TTSManager shutdown 완료")
    }

    // -----------------------------
    // Internal core
    // -----------------------------

    private suspend fun speakInternal(text: String, queueMode: Int, utteranceId: String) {
        withContext(Dispatchers.Main) {
            val engine = tts ?: return@withContext
            try {
                engine.speak(text, queueMode, /* params */ null, utteranceId)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "TTS speak 실패")
                isSpeakingFlag.set(false)
            }
        }
    }
}
