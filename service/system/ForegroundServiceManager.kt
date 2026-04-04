package com.example.rush_hz_plus.service.system


import android.content.Context
import android.content.Intent
import com.example.rush_hz_plus.ui.alert.FullScreenAlarmActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FGS 상태 관리 및 알림 표시 전용 매니저
 *
 * ⚠️ 주의: Android 14+에서는 FGS 시작 로직을 완전히 제거
 * - FGS 시작은 오직 UI 계층 (DashboardFragment 버튼 클릭) 에서만 수행
 * - 이 클래스는 서비스 실행 상태 추적 및 전체화면 알림 표시만 담당
 */
@Singleton
class ForegroundServiceManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    // 상태 관리 필드 (외부에서 접근 가능)
    var isAudioMonitorServiceRunning: Boolean = false
        private set // 외부에서 직접 수정 불가, UI 계층에서만 업데이트

    /**
     * L3 감지 시 전체화면 경고 팝업 표시
     *
     * ⚠️ 이 메서드는 시스템 알림/긴급 상황에서 호출되며,
     * Activity Context가 아닌 Application Context로 실행됨
     */
    fun showFullScreenAlarm(message: String) {
        try {
            val intent = Intent(appContext, FullScreenAlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("ALARM_MESSAGE", message)
            }
            appContext.startActivity(intent)
            Timber.tag("FgSvcMgr").d("FullScreenAlarmActivity 실행")
        } catch (e: Exception) {
            Timber.tag("FgSvcMgr").e(e, "FullScreenAlarmActivity 실행 실패")
        }
    }
}