// model/TFLiteInterpreter.kt
package com.example.rush_hz_plus.model

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import androidx.annotation.WorkerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TFLite 추론 엔진
 * 입력: [16000] float32 (1초 오디오)
 * 출력: [6] float32 (위험 소리 확률)
 *
 * - YAMNet + Classifier 통합 모델 사용
 * - NDK, JNI, C++ 필요 없음
 */
@Singleton
class TFLiteInterpreter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var interpreter: Interpreter? = null
    private val modelLoaded = AtomicBoolean(false)
    private val lock = Any()

    // 모델 출력 클래스 수 (훈련 시 설정한 라벨 개수와 동일해야 함)
    private val expectedOutputClasses = 7

    // 개발/현장 디버깅 옵션
    private val useNnApi: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .getBoolean("enable_nnapi", false)
    }

    suspend fun ensureLoaded() {
        if (modelLoaded.get()) return
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (!modelLoaded.get()) {
                    loadModelInternal()
                    modelLoaded.set(interpreter != null)
                }
            }
        }
    }

    @WorkerThread
    private fun loadModelInternal() {
        try {
            val modelPath = "exported_model/yamnet_classifier.tflite"
            val buffer = context.assets.openFd(modelPath).use { fd ->
                FileInputStream(fd.fileDescriptor).use { fis ->
                    fis.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        fd.startOffset,
                        fd.declaredLength
                    )
                }
            }

            val options = Interpreter.Options().apply {
                numThreads = 4
                @Suppress("DEPRECATION")
                setUseXNNPACK(false)  // 핵심: XNNPACK 비활성화
                if (useNnApi) {
                    @Suppress("DEPRECATION")
                    setUseNNAPI(true)
                }
            }

            interpreter = Interpreter(buffer, options)

            // 형상 검증 (리사이즈 없음)
            val inTensor = interpreter!!.getInputTensor(0)
            val inputShape = inTensor.shape()
            val inputOk = inputShape.contentEquals(intArrayOf(15600)) ||
                    inputShape.contentEquals(intArrayOf(1, 15600))
            require(inputOk) {
                "입력 형상 불일치: ${inputShape.contentToString()}"
            }

            Timber.i(
                "✅ TFLite 모델 로드 완료: %s | Input: %s | NNAPI=%s",
                modelPath, inputShape.contentToString(), useNnApi
            )
        } catch (e: Exception) {
            Timber.e(e, "❌ TFLite 모델 로드 실패 — interpreter=null")
            interpreter = null
        }
    }

    /**
     * Float PCM(ShortArray → -1..1 scaling) 입력으로 추론 수행
     * 결과: FloatArray(size = expectedOutputClasses)
     */
    suspend fun runInference(pcmBuffer: ShortArray): FloatArray =
        withContext(Dispatchers.IO) {
            ensureLoaded()
            synchronized(lock) {
                if (interpreter == null) {
                    Timber.w("⚠️ Interpreter가 null입니다. 재로드 실패 상태 — 안전한 0 벡터 반환")
                    return@synchronized FloatArray(expectedOutputClasses) { 0f }
                }

                // 2D 입력 전달 (유지)
                // 16000 → 15600 변환
                val input15600 = FloatArray(15600)
                for (i in 0 until 15600) {
                    input15600[i] = if (i < pcmBuffer.size) pcmBuffer[i] / 32768f else 0f
                }
                val floatInput2D = arrayOf(input15600)

                // 1D 출력 버퍼로 변경
                val output = FloatArray(expectedOutputClasses)

                val t0 = System.nanoTime()
                return@synchronized try {
                    // 1D 출력 버퍼 사용
                    interpreter!!.run(floatInput2D, output)
                    val ms = (System.nanoTime() - t0) / 1_000_000f
                    Timber.d("🧠 TFLite 추론 완료 (%.1fms) → %s", ms, output.joinToString(", ") { "%.3f".format(it) })
                    output
                } catch (e: Exception) {
                    Timber.e(e, "❌ Inference 실패 — 0 벡터 반환")
                    FloatArray(expectedOutputClasses) { 0f }
                }
            }
        }

    fun isLoaded(): Boolean = modelLoaded.get()

    fun close() {
        synchronized(lock) {
            interpreter?.close()
            interpreter = null
            modelLoaded.set(false)
            Timber.i("🧹 TFLite Interpreter 해제 완료")
        }
    }
}
