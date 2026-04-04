// domain/score/KeywordDetector.kt
package com.example.rush_hz_plus.domain.score

import android.annotation.SuppressLint
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeywordDetector @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private const val MODEL_ASSET_PATH = "vosk-model-small-ko-0.22"
        private const val MODEL_LOCAL_DIR = "vosk-model-small-ko-0.22"
        private val KEYWORDS = setOf("불이야", "불났", "도와", "살려", "신고", "경찰", "119", "위험", "구해", "사고")
    }

    // 모델 상태 플래그 — 복사/로드 성공 여부 추적
    private var isModelReady = false
    private var modelLoadAttempted = false

    private val modelPath: String by lazy {
        val localDir = File(context.filesDir, MODEL_LOCAL_DIR)
        if (!localDir.exists()) {
            try {
                Timber.i("Vosk 모델 복사 시작")
                copyAssetsRecursively(MODEL_ASSET_PATH, localDir)
                isModelReady = true
                Timber.i("Vosk 모델 복사 완료")
            } catch (e: Exception) {
                Timber.e(e, "❌ Vosk 모델 복사 실패 — 키워드 감지 비활성화")
                isModelReady = false
            }
        } else {
            isModelReady = true
        }
        modelLoadAttempted = true
        localDir.absolutePath
    }

    private val model: Model? by lazy {
        if (!isModelReady) return@lazy null
        try {
            val m = Model(modelPath)
            Timber.i("Vosk 모델 로드 성공")
            m
        } catch (e: Exception) {
            Timber.e(e, "❌ Vosk 모델 로드 실패 — 키워드 감지 비활성화")
            isModelReady = false
            null
        }
    }

    /**
     * 항상 안전하게 실행 — 실패 시 (false, null) 반환
     */
    suspend fun detectKeywords(pcmBuffer: ShortArray): Pair<Boolean, String?> = withContext(Dispatchers.Default) {
        // 모델 준비되지 않음 → 즉시 false 반환
        if (!modelLoadAttempted || !isModelReady) {
            return@withContext false to null
        }

        val recognizer = model?.let { Recognizer(it, 16000f) } ?: return@withContext false to null

        try {
            val audioBytes = shortArrayToByteArray(pcmBuffer)
            if (recognizer.acceptWaveForm(audioBytes, audioBytes.size)) {
                val result = JSONObject(recognizer.result)
                val text = result.optString("text", "").lowercase().trim()
                if (text.isNotEmpty()) {
                    val matched = KEYWORDS.firstOrNull { text.contains(it) }
                    if (matched != null) {
                        return@withContext true to matched
                    }
                }
            }
            false to null
        } catch (e: Exception) {
            Timber.e(e, "키워드 감지 중 오류 — 기능 비활성화")
            isModelReady = false // 재시도 방지
            false to null
        } finally {
            recognizer.close()
        }
    }

    /**
     * 설정 화면에서 사용자에게 상태 표시용
     */
    fun isKeywordDetectionAvailable(): Boolean = isModelReady

    private fun shortArrayToByteArray(shorts: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        shorts.forEach { buffer.putShort(it) }
        return buffer.array()
    }

    /**
     * assets 폴더를 재귀적으로 filesDir로 복사
     */
    private fun copyAssetsRecursively(assetPath: String, outputDir: File) {
        outputDir.mkdirs()
        val assets = context.assets.list(assetPath) ?: throw IllegalStateException("Assets not found: $assetPath")

        for (asset in assets) {
            val fullPath = "$assetPath/$asset"
            val outFile = File(outputDir, asset)
            val subAssets = context.assets.list(fullPath)
            if (subAssets != null && subAssets.isNotEmpty()) {
                copyAssetsRecursively(fullPath, outFile)
            } else {
                context.assets.open(fullPath).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}