// ui/splash/SplashFragment.kt
package com.example.rush_hz_plus.ui.splash

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.databinding.FragmentSplashBinding


@SuppressLint("CustomSplashScreen")
class SplashFragment : Fragment() {
    private var handler: Handler? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentSplashBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        handler = Handler(Looper.getMainLooper())
        handler?.postDelayed({
            // Fragment가 아직 attached되어 있는지 확인
            if (isAdded && !isDetached && activity != null) {
                val prefs = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                val onboardingCompleted = prefs.getBoolean("onboarding_completed", false)

                if (onboardingCompleted) {
                    findNavController().navigate(R.id.action_splash_to_login)
                } else {
                    findNavController().navigate(R.id.action_splash_to_onboarding)
                }
            }
        }, 3000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler?.removeCallbacksAndMessages(null)
        handler = null
    }
}