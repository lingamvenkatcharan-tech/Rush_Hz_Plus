package com.example.rush_hz_plus.service.alert

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlashAlert @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "FlashAlert"
    }

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var cameraId: String? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 현재 플래시 점멸 상태를 추적 (중복 방지)
     */
    private val isFlashing = AtomicBoolean(false)

    init {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            cameraId = getCameraId()
            Timber.tag(TAG).d("FlashAlert 초기화 완료 (cameraId=$cameraId)")
        } else {
            Timber.tag(TAG).w("이 기기는 플래시 기능을 지원하지 않습니다.")
        }
    }

    private fun getCameraId(): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: CameraAccessException) {
            Timber.tag(TAG).e(e, "Camera ID 조회 실패")
            null
        }
    }

    /**
     * 한정된 횟수만 점멸 (중복 호출 방지 포함)
     */
    @Synchronized
    fun blink(times: Int, delay: Long) {
        if (isFlashing.get()) {
            Timber.tag(TAG).d("이미 점멸 중이므로 blink() 호출 무시")
            return
        }

        stopBlinking()
        if (cameraId == null) {
            Timber.tag(TAG).w("카메라 ID 없음 — 점멸 생략")
            return
        }

        isFlashing.set(true)
        job = scope.launch {
            Timber.tag(TAG).i("플래시 점멸 시작 ($times 회)")
            try {
                repeat(times) { i ->
                    toggleFlash(true)
                    delay(delay)
                    toggleFlash(false)
                    if (i < times - 1) delay(delay)
                }
            } catch (e: CancellationException) {
                Timber.tag(TAG).d("플래시 점멸이 취소됨")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "플래시 점멸 오류")
            } finally {
                isFlashing.set(false)
                toggleFlash(false)
                Timber.tag(TAG).i("플래시 점멸 종료")
            }
        }
    }

    /**
     * 무한 점멸 (L3 긴급 상황 등에서 사용)
     */
    @Synchronized
    fun blinkContinuous(interval: Long) {
        if (isFlashing.get()) {
            Timber.tag(TAG).d("이미 점멸 중이므로 blinkContinuous() 무시")
            return
        }

        if (cameraId == null) {
            Timber.tag(TAG).w("카메라 ID 없음 — 점멸 생략")
            return
        }

        isFlashing.set(true)
        job = scope.launch {
            Timber.tag(TAG).i("플래시 무한 점멸 시작 (interval=${interval}ms)")
            try {
                while (isFlashing.get()) {
                    toggleFlash(true)
                    delay(interval)
                    toggleFlash(false)
                    delay(interval)
                }
            } catch (e: CancellationException) {
                Timber.tag(TAG).d("플래시 점멸 취소됨")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "플래시 점멸 오류")
            } finally {
                toggleFlash(false)
                isFlashing.set(false)
                Timber.tag(TAG).i("플래시 점멸 종료")
            }
        }
    }

    /**
     * 플래시 상태 토글
     */
    @Synchronized
    private fun toggleFlash(enable: Boolean) {
        if (cameraId == null) return
        try {
            cameraManager.setTorchMode(cameraId!!, enable)
            Timber.tag(TAG).v("플래시 상태: ${if (enable) "ON" else "OFF"}")
        } catch (e: CameraAccessException) {
            Timber.tag(TAG).e(e, "플래시 접근 실패")
        } catch (e: SecurityException) {
            Timber.tag(TAG).e(e, "플래시 권한 없음")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "플래시 제어 실패")
        }
    }

    /**
     * 점멸 중지 (idempotent)
     */
    @Synchronized
    fun stopBlinking() {
        if (!isFlashing.get() && job == null) {
            Timber.tag(TAG).d("점멸 중이 아니므로 stopBlinking() 무시")
            return
        }

        try {
            job?.cancel()
            toggleFlash(false)
            Timber.tag(TAG).i("플래시 점멸 중지 요청 완료")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "점멸 중지 실패")
        } finally {
            isFlashing.set(false)
            job = null
        }
    }
}
