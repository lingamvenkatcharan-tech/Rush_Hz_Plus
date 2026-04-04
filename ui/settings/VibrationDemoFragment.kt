// ui/settings/VibrationDemoFragment.kt
package com.example.rush_hz_plus.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.rush_hz_plus.databinding.FragmentVibrationDemoBinding
import com.example.rush_hz_plus.service.alert.AlertManager
import com.example.rush_hz_plus.service.alert.VibrationPattern
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint // Hilt 활성화
class VibrationDemoFragment : Fragment() {

    private var _binding: FragmentVibrationDemoBinding? = null
    private val binding get() = _binding!!

    @Inject // AlertManager 주입
    lateinit var alertManager: AlertManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVibrationDemoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 뒤로가기 버튼
        binding.imageBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 3단계 (긴급) - HIGH 패턴
        binding.vibrationCheckButton5.setOnClickListener {
            alertManager.demoVibration(VibrationPattern.HIGH)
        }

        // 2단계 (경고) - MEDIUM 패턴
        binding.vibrationCheckButton4.setOnClickListener {
            alertManager.demoVibration(VibrationPattern.MEDIUM)
        }

        // 1단계 (경계) - LOW 패턴
        binding.vibrationCheckButton3.setOnClickListener {
            alertManager.demoVibration(VibrationPattern.LOW)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}