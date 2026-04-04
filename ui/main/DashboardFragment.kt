package com.example.rush_hz_plus.ui.main

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.databinding.FragmentDashboardBinding
import com.example.rush_hz_plus.service.system.PermissionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var permissionManager: PermissionManager

    /** 권한 요청 런처 - registerForActivityResult 사용 */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (permissionManager.hasAllPermissions(requireContext())) {
            // 권한 허용됨 → 모니터링 시작 시도
            toggleMonitoring()
        } else {
            // 권한 거부됨 → 사용자에게 안내
            showPermissionDeniedDialog()
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDashboardBinding.bind(view)

        Timber.d("🎯 DashboardFragment: 구독 시작")

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Timber.d("🎯 DashboardFragment: 구독 활성화됨")
                viewModel.dashboardState.collect { state ->
                    Timber.d("🎯 DashboardFragment: UI 업데이트 - ${state.emoji}")
                    updateDashboardUI(state)
                }
            }
        }

        // 모니터링 상태 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isMonitoringActive.collect { isActive ->
                    updateMonitoringToggleButton(isActive)
                }
            }
        }

        // 실시간 대시보드 상태
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dashboardState.collect { state ->
                    updateDashboardUI(state)
                }
            }
        }

        // 모니터링 토글 버튼
        binding.toggleMonitoringButton.setOnClickListener {
            toggleMonitoring()
        }

        // 상단 메뉴 클릭 리스너
        binding.imageAlarm.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_alarm)
        }
        binding.imageProfile.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_userMenu)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun toggleMonitoring() {
        if (viewModel.isMonitoringActive.value) {
            // 모니터링 중지
            viewModel.stopMonitoring(requireContext())
        } else {
            // 모니터링 시작
            if (permissionManager.hasAllPermissions(requireContext())) {
                try {
                    viewModel.startMonitoring(requireActivity())
                } catch (e: SecurityException) {
                    showSecurityExceptionDialog()
                } catch (e: Exception) {
                    showStartFailedDialog()
                }
            } else {
                // 권한 요청 (registerForActivityResult 사용)
                requestPermissions()
            }
        }
    }

    /** registerForActivityResult 기반 권한 요청 */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)

        // Android 13+(TIRAMISU, API 33)부터 추가 권한 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun updateMonitoringToggleButton(isActive: Boolean) {
        binding.toggleMonitoringButton.setText(
            if (isActive) R.string.stop_monitoring else R.string.start_monitoring
        )

        binding.toggleMonitoringButton.contentDescription = if (isActive) {
            "위험 소리 감지 중지"
        } else {
            "위험 소리 감지 시작"
        }
    }

    private fun updateDashboardUI(state: DashboardState) {
        binding.whiteCircleWithBorder.text = state.emoji
        binding.safetyTextView.text = state.levelText
        binding.textLog.text = state.lastDetectedText
        binding.greenCircleView.setBackgroundResource(
            if (state.isSafe) R.drawable.green_circle else R.drawable.red_circle
        )
        binding.greenCircleView.contentDescription = if (state.isSafe) {
            "안전 상태"
        } else {
            "위험 상태 감지됨"
        }
    }

    private fun showSecurityExceptionDialog() {
        // Android 14+ SecurityException 안내 다이얼로그
        // 구현 필요 시 AlertDialog 사용
    }

    private fun showStartFailedDialog() {
        // 시작 실패 안내 다이얼로그
        // 구현 필요 시 AlertDialog 사용
    }

    private fun showPermissionDeniedDialog() {
        // 권한 거부 안내 다이얼로그
        // 구현 필요 시 AlertDialog 사용
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}