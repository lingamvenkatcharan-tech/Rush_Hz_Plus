package com.example.rush_hz_plus.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.rush_hz_plus.databinding.FragmentFlashDemoBinding
import com.example.rush_hz_plus.service.alert.AlertManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FlashDemoFragment : Fragment() {

    private var _binding: FragmentFlashDemoBinding? = null
    private val binding get() = _binding!!

    // Hilt로 주입된 AlertManager 사용
    @Inject
    lateinit var alertManager: AlertManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFlashDemoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🔙 뒤로가기 버튼
        binding.imageBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 💡 각 단계별 플래시 데모 실행 버튼
        binding.flashCheckButton1.setOnClickListener {
            // 1단계: 약한 플래시 패턴
            alertManager.demoFlash(blinkCount = 2, delay = 300L)
        }

        binding.flashCheckButton2.setOnClickListener {
            // 2단계: 중간 세기 플래시
            alertManager.demoFlash(blinkCount = 4, delay = 200L)
        }

        binding.flashCheckButton3.setOnClickListener {
            // 3단계: 긴급 상황용 빠른 점멸
            alertManager.demoFlash(blinkCount = 6, delay = 150L)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
