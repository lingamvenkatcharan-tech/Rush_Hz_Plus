package com.example.rush_hz_plus.worker

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkerScheduler @Inject constructor(
    private val application: Application
) {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(application)

    /**
     * 감지 로그 백업 워커 스케줄링
     * - 15분마다 실행
     * - 네트워크 연결 필요
     * - 기존 작업 유지 (중복 스케줄 방지)
     */
    fun scheduleBackupWorker() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val backupWork = PeriodicWorkRequestBuilder<DetectionLogBackupWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    10,
                    TimeUnit.MINUTES
                )
                .addTag("DetectionBackup")
                .build()

            workManager.enqueueUniquePeriodicWork(
                "DetectionLogBackup",
                ExistingPeriodicWorkPolicy.KEEP,
                backupWork
            )

            Timber.tag("WorkerScheduler").i("✅ DetectionLogBackupWorker 스케줄링 완료")
        } catch (e: Exception) {
            Timber.tag("WorkerScheduler").e(e, "❌ 백업 워커 스케줄링 실패")
        }
    }

    /**
     * 수동 실행용 (즉시 백업)
     */
    fun runImmediateBackup() {
        val oneTimeWork = OneTimeWorkRequestBuilder<DetectionLogBackupWorker>().build()
        workManager.enqueue(oneTimeWork)
        Timber.tag("WorkerScheduler").i("🕒 즉시 백업 워커 실행됨")
    }

    /**
     * 백업 워커 취소
     */
    fun cancelBackupWorker() {
        workManager.cancelUniqueWork("DetectionLogBackup")
        Timber.tag("WorkerScheduler").i("🛑 DetectionLogBackupWorker 취소됨")
    }
}
