package com.example.rush_hz_plus.ui.splash

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.ui.main.MainActivity

// ui/splash/OnboardingFragment.kt
class OnboardingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btn_request_permission).setOnClickListener {
            // 온보딩 완료 플래그 저장
            requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                .edit().putBoolean("onboarding_completed", true).apply()

            // nav_auth.xml 기반 네비게이션
            findNavController().navigate(R.id.action_onboarding_to_login)
        }
    }
}