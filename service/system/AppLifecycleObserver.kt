package com.example.rush_hz_plus.service.system

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import timber.log.Timber
import javax.inject.Inject

/**
 * 앱 생명주기 이벤트를 관찰하여 상태를 로깅하는 옵저버
 *
 * ⚠️ 주의: Android 14+에서는 생명주기 콜백 내에서 FGS 시작 시도 금지
 * - FGS 시작은 오직 사용자 직접 동작 (UI 버튼 클릭, 접근성 제스처) 에서만 허용
 * - 이 클래스는 순수 상태 관찰 및 디버깅 로깅만 담당
 */
@ActivityRetainedScoped
class AppLifecycleObserver @Inject constructor(
    @ApplicationContext private val context: Context
) : DefaultLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        // 상태 로깅만 수행 (디버깅 목적)
        Timber.d("AppLifecycle", "앱이 포그라운드로 전환됨 (RESUMED)")

        // ⚠️ 모든 액션 실행 제거
        // - FGS 시작 시도 금지 (Android 14+ 정책 위반)
        // - 접근성 상태 확인 및 분기 로직 제거
        // - 서비스 시작 책임은 UI 계층이 담당
    }

    override fun onStop(owner: LifecycleOwner) {
        // 상태 로깅만 수행 (디버깅 목적)
        Timber.d("AppLifecycle", "앱이 백그라운드로 전환됨 (STOPPED)")

        // ⚠️ 서비스 중지 로직 없음 (접근성 앱 요구사항 충족)
        // - AudioMonitorService는 백그라운드에서도 계속 실행되어야 함
    }
}