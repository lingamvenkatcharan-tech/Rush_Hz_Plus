package com.example.rush_hz_plus

import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.rush_hz_plus.data.repository.DetectionRepositoryImpl
import com.example.rush_hz_plus.service.system.PermissionManager
import com.example.rush_hz_plus.worker.WorkerScheduler
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class RushHzPlusApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerScheduler: WorkerScheduler
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var detectionRepository: DetectionRepositoryImpl
    @Inject lateinit var permissionManager: PermissionManager

    // ⚠️ foregroundServiceManager 제거: ActivityLifecycleCallbacks 구현 없음

    override fun onCreate() {
        super.onCreate()

        // === Timber 로그 초기화 ===
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        Timber.i("🚀 RushHzPlusApplication onCreate() 초기화 시작")

        // === Activity 라이프사이클 등록 제거 ===
        // registerActivityLifecycleCallbacks(foregroundServiceManager) // 제거

        // === 권한 상태 로그 출력 ===
        logPermissionStatus()

        // === WorkManager 주기적 백업 워커 스케줄링 ===
        try {
            workerScheduler.scheduleBackupWorker()
            Timber.i("✅ 백업 워커 스케줄링 완료")
        } catch (e: Exception) {
            Timber.e(e, "❌ WorkManager 스케줄링 실패")
        }

        // === Android 14 이상 Foreground 서비스 정책 대응 ===
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Timber.i("📱 Android 14+ 환경: 포그라운드 서비스 제약 자동 대응 활성화됨")
        }

        Timber.i("✅ RushHzPlusApplication 초기화 완료")
    }

    /**
     * WorkManager에서 사용할 Hilt WorkerFactory 설정
     */
    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * 앱 종료 시 리소스 정리
     */
    override fun onTerminate() {
        super.onTerminate()
        Timber.w("🧹 Application 종료 감지 — DB 리소스 해제 중...")
        detectionRepository.close()
    }

    /**
     * 현재 주요 권한 상태를 로그로 출력
     */
    private fun logPermissionStatus() {
        val context = this
        val permissions = mapOf(
            "RECORD_AUDIO" to permissionManager.hasPermission(context, android.Manifest.permission.RECORD_AUDIO),
            "POST_NOTIFICATIONS" to permissionManager.hasNotificationPermission(context),
            "VIBRATE" to permissionManager.hasVibrationCapability(context)
        )

        // ACCESS_FINE_LOCATION 제거: 권한 자체가 제거되었음
        permissions.forEach { (perm, granted) ->
            Timber.i("🔐 권한 상태: $perm = ${if (granted) "허용됨" else "거부됨"}")
        }
    }
}