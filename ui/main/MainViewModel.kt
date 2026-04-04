package com.example.rush_hz_plus.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rush_hz_plus.core.utils.DateUtils
import com.example.rush_hz_plus.data.repository.DetectionRepositoryInterface
import com.example.rush_hz_plus.domain.entity.DetectionResult
import com.example.rush_hz_plus.domain.score.ClassScorer
import com.example.rush_hz_plus.service.monitor.AudioMonitorService
import com.example.rush_hz_plus.service.system.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val detectionRepository: DetectionRepositoryInterface,
    private val permissionManager: PermissionManager
) : ViewModel() {

    // L3 이벤트
    val emergencyEvent = detectionRepository.emergencyEvent
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 0)

    // 모니터링 활성화 상태
    private val _isMonitoringActive = MutableStateFlow(false)
    val isMonitoringActive: StateFlow<Boolean> = _isMonitoringActive.asStateFlow()

    // 직접 StateFlow 관리
    private val _dashboardState = MutableStateFlow(DashboardState.safe())
    val dashboardState: StateFlow<DashboardState> = _dashboardState

    init {
        // 강제 구독 시작
        viewModelScope.launch {
            detectionRepository.latestRealtimeDetection.collect { detection ->
                Timber.d("📊 Dashboard: Detection received - ${detection?.soundLabel}")
                val newState = detection?.toDashboardState() ?: DashboardState.safe()
                Timber.d("📊 Dashboard: State updated - emoji=${newState.emoji}, isSafe=${newState.isSafe}")
                _dashboardState.value = newState
            }
        }
    }

    /**
     * 오디오 모니터링 시작
     *
     * ⚠️ Android 14+ 정책 준수:
     * - 오직 Activity Context에서만 호출되어야 함
     * - 사용자 직접 동작 (버튼 클릭) 에서만 호출되어야 함
     */
    fun startMonitoring(activity: Activity) {
        if (!permissionManager.hasAllPermissions(activity)) {
            // 권한 부족 시 외부에서 처리 (DashboardFragment에서 권한 요청)
            return
        }

        try {
            val intent = Intent(activity, AudioMonitorService::class.java)
            activity.startForegroundService(intent)
            _isMonitoringActive.value = true
        } catch (e: Exception) {
            // SecurityException 등 예외 처리
            _isMonitoringActive.value = false
            // 외부에서 에러 처리 (DashboardFragment에서 알림 표시)
            throw e
        }
    }

    /**
     * 오디오 모니터링 중지
     */
    fun stopMonitoring(context: Context) {
        try {
            val intent = Intent(context, AudioMonitorService::class.java)
            context.stopService(intent)
            _isMonitoringActive.value = false
        } catch (e: Exception) {
            // 중지 실패 시 상태만 업데이트
            _isMonitoringActive.value = false
        }
    }

    /**
     * 현재 모니터링 활성화 상태 토글
     *
     * ⚠️ 주의: 이 메서드는 외부에서 직접 호출하지 말고,
     * startMonitoring()/stopMonitoring()을 개별적으로 호출할 것
     */
    fun toggleMonitoring(activity: Activity, context: Context) {
        if (_isMonitoringActive.value) {
            stopMonitoring(context)
        } else {
            startMonitoring(activity)
        }
    }
}

data class DashboardState(
    val emoji: String,
    val label: String,
    val levelText: String,
    val lastDetectedText: String,
    val isSafe: Boolean
) {
    companion object {
        fun safe(): DashboardState = DashboardState(
            emoji = "✅",
            label = "안전",
            levelText = "안전",
            lastDetectedText = "아직 감지된 위험 소리 없음",
            isSafe = true
        )
    }
}

private fun DetectionResult.toDashboardState(): DashboardState {
    val label = ClassScorer.getLabel(detectedClass)
    val levelText = hazardScore.toDisplayText()

    // ★ 모델이 SAFE 출력 시 항상 안전 상태
    val emoji = if (detectedClass == 0 || hazardScore.isSafe()) {
        "✅"  // 일관된 안전 이모지
    } else {
        ClassScorer.getEmoji(detectedClass)
    }

    return DashboardState(
        emoji = emoji,
        label = label,
        levelText = levelText,
        lastDetectedText = "마지막 감지: ${DateUtils.formatRelative(timestamp)}",
        isSafe = hazardScore.isSafe()
    )
}