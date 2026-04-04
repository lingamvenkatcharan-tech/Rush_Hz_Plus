package com.example.rush_hz_plus.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.core.utils.AccessibilityUtil
import com.example.rush_hz_plus.data.repository.UserProfileRepository
import com.example.rush_hz_plus.databinding.ActivityMainBinding
import com.example.rush_hz_plus.service.emergency.GuardianAlertListenerService
import com.example.rush_hz_plus.service.system.AppLifecycleObserver
import com.example.rush_hz_plus.service.system.PermissionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var permissionManager: PermissionManager
    @Inject lateinit var appLifecycleObserver: AppLifecycleObserver
    @Inject lateinit var userProfileRepository: UserProfileRepository

    // 비동기 작업 관리를 위한 Job
    private val guardianModeJob = Job()

    /** Android 13+ 알림 포함, 마이크/FGS_MIC 권한을 한 번에 요청 */
    private val requestAllPermsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            Timber.tag("Main").i("권한 결과 처리 완료")

            // FGS 시작 시도 제거: 권한 상태만 업데이트
            if (permissionManager.hasAllPermissions(this)) {
                // 권한 완료 후 접근성 안내만 표시
                checkAndRequestAccessibilityService()
                // FGS 시작은 DashboardFragment 버튼에서만 수행
            } else {
                showGoToSettingsDialog()
            }
        }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag("Main").i("onCreate")
        setContentView(ActivityMainBinding.inflate(layoutInflater).root)
        lifecycle.addObserver(appLifecycleObserver)

        handleEmergencyIntent(intent)
        permissionManager.logPermissionStatus(this)

        // 권한 확인 및 요청 (FGS 시작 시도 제거)
        if (!permissionManager.hasAllPermissions(this)) {
            // 권한이 없으면 권한 요청
            val request = PermissionManager.REQUIRED_PERMISSIONS
            Timber.tag("Main").i("요청할 권한: ${request.joinToString()}")
            requestAllPermsLauncher.launch(request)
        } else {
            // 권한이 있으면 접근성 안내만 표시
            checkAndRequestAccessibilityService()
            // FGS 시작 시도 완전 제거

            // 보호자 역할 확인 후 자동 시작
            startGuardianModeIfNeeded()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleEmergencyIntent(intent)
    }

    private fun handleEmergencyIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("show_emergency_dialog", false) == true) {
            val navController = findNavController(R.id.nav_host_fragment)
            if (navController.currentDestination?.id != R.id.alarmFragment) {
                // 🔧 Navigation 개선: 중복 Fragment 방지
                navController.popBackStack(R.id.dashboardFragment, false)
                navController.navigate(R.id.alarmFragment)
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("마이크 권한이 필요합니다")
            .setMessage("위험 소리 감지 기능을 사용하려면 마이크 접근 권한이 필요합니다.")
            .setPositiveButton("권한 설정") { _, _ ->
                requestAllPermsLauncher.launch(PermissionManager.REQUIRED_PERMISSIONS)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("권한 설정 필요")
            .setMessage("설정에서 '마이크' 권한을 허용해 주세요.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun checkAndRequestAccessibilityService() {
        val isEnabled = AccessibilityUtil.isAccessibilityServiceEnabled(this)

        if (isEnabled) return

        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val hasShownAccessibilityDialog = prefs.getBoolean("has_shown_accessibility_dialog", false)

        if (!hasShownAccessibilityDialog) {
            AlertDialog.Builder(this)
                .setTitle("접근성 서비스 활성화 필요")
                .setMessage("청각장애인을 위한 위험 소리 감지 기능을 사용하려면, 접근성 서비스를 활성화해 주세요.")
                .setPositiveButton("설정으로 이동") { _, _ ->
                    prefs.edit().putBoolean("has_shown_accessibility_dialog", true).apply()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("나중에") { _, _ ->
                    prefs.edit().putBoolean("has_shown_accessibility_dialog", true).apply()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun startGuardianModeIfNeeded() {
        lifecycleScope.launch(guardianModeJob) {
            val role = userProfileRepository.getUserRole()
            if (role == "guardian") {
                // Android 14+ FGS 권한 확인
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    if (!permissionManager.hasPermission(
                            this@MainActivity,
                            Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
                        )
                    ) {
                        Timber.w("FGS 권한 없음 — GuardianAlertListenerService 시작 생략")
                        return@launch
                    }
                }

                val intent = Intent(this@MainActivity, GuardianAlertListenerService::class.java)
                ContextCompat.startForegroundService(this@MainActivity, intent)
                Timber.i("보호자 모드(FGS) 자동 시작됨")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        guardianModeJob.cancel() // 메모리 누수 방지
    }

}