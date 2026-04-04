package com.example.rush_hz_plus.worker


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.data.repository.DetectionRepositoryImpl
import com.example.rush_hz_plus.service.monitor.AudioMonitorService
import com.example.rush_hz_plus.service.system.PermissionManager
import com.example.rush_hz_plus.ui.main.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class DetectionLogBackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val repository: DetectionRepositoryImpl,
    private val permissionManager: PermissionManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Timber.tag("BackupWorker").d("🔄 감지 로그 백업 및 마이크 FGS 재시도 워커 시작")

        // 네트워크 감지 로그 백업
        try {
            if (!isNetworkAvailable()) {
                Timber.tag("BackupWorker").w("📶 네트워크 미연결 → 백업 스킵 (성공 처리)")
            } else {
                repository.syncPendingDetections()
                Timber.tag("BackupWorker").i("✅ 감지 로그 동기화 성공")
                return@withContext Result.success()
            }

            Result.success()
        } catch (e: Exception) {
            Timber.tag("BackupWorker").e(e, "❌ 감지 로그 백업 실패")
            // 백업 실패는 retry 대상
            return@withContext Result.retry()
        }
    }

    // ====== UTILITIES ======

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
