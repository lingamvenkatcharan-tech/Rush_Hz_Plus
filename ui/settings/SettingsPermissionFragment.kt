//// ui/settings/SettingsPermissionFragment.kt
//package com.example.rush_hz_plus.ui.settings
//
//import android.app.Dialog
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.Toast
//import androidx.core.content.ContextCompat
//import androidx.fragment.app.Fragment
//import androidx.fragment.app.viewModels
//import androidx.lifecycle.lifecycleScope
//import androidx.navigation.fragment.findNavController
//import com.example.rush_hz_plus.R
//import com.example.rush_hz_plus.core.utils.PreferenceManager
//import com.example.rush_hz_plus.databinding.DialogWithdrawBinding
//import com.example.rush_hz_plus.databinding.FragmentSettingsPermissionBinding
//import com.example.rush_hz_plus.service.system.PermissionManager
//import com.example.rush_hz_plus.ui.auth.AuthViewModel
//import dagger.hilt.android.AndroidEntryPoint
//import kotlinx.coroutines.launch
//import javax.inject.Inject
//
//@AndroidEntryPoint
//class SettingsPermissionFragment : Fragment() {
//
//    private var _binding: FragmentSettingsPermissionBinding? = null
//    private val binding get() = _binding!!
//
//    private val authViewModel: AuthViewModel by viewModels()
//
//    // @Inject로 의존성 주입
//    @Inject
//    lateinit var preferenceManager: PreferenceManager
//
//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View {
//        _binding = FragmentSettingsPermissionBinding.inflate(inflater, container, false)
//        return binding.root
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        updatePermissionStatus()
//
//        binding.btnRequestMic.setOnClickListener {
//            preferenceManager.requestPermissions(requireActivity(), preferenceManager.REQUEST_CODE_REQUIRED_PERMISSIONS)
//        }
//
//        binding.btnRequestNotification.setOnClickListener {
//            preferenceManager.requestNotificationPermission(this)
//        }
//
//        // L3 자동 TTS 설정
//        lifecycleScope.launch {
//            preferenceManager.autoTtsOnL3Flow.collect { enabled ->
//                binding.switchAutoTtsL3.isChecked = enabled
//            }
//        }
//
//        binding.switchAutoTtsL3.setOnCheckedChangeListener { _, isChecked ->
//            lifecycleScope.launch {
//                preferenceManager.setAutoTtsOnL3(isChecked)
//            }
//        }
//
//        // 회원 탈퇴 버튼 추가 (레이아웃에 btn_withdraw 추가 필요)
//        binding.btnWithdraw.setOnClickListener {
//            showWithdrawDialog()
//        }
//
//        // 탈퇴 결과 관찰
//        viewLifecycleOwner.lifecycleScope.launch {
//            authViewModel.authActionState.collect { state ->
//                when (state) {
//                    is AuthViewModel.AuthState.Loading -> {
//                        // 로딩 UI (옵션)
//                    }
//                    is AuthViewModel.AuthState.WithdrawSuccess -> {
//                        Toast.makeText(context, "회원 탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()
//                        // 로그아웃 후 스플래시로 이동
//                        findNavController().popBackStack(R.id.splashFragment, false)
//                    }
//                    is AuthViewModel.AuthState.Error -> {
//                        Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
//                    }
//                    else -> Unit
//                }
//            }
//        }
//    }
//
//    private fun showWithdrawDialog() {
//        val dialogBinding = DialogWithdrawBinding.inflate(layoutInflater)
//        val dialog = Dialog(requireContext()).apply {
//            setContentView(dialogBinding.root)
//            setCancelable(true)
//            window?.setBackgroundDrawableResource(android.R.color.transparent)
//        }
//
//        dialogBinding.btnConfirmWithdraw.setOnClickListener {
//            val email = dialogBinding.etEmail.text.toString().trim()
//            val password = dialogBinding.etPassword.text.toString().trim()
//            authViewModel.withdrawAccount(email, password)
//            dialog.dismiss()
//        }
//
//        dialogBinding.btnCancel.setOnClickListener {
//            dialog.dismiss()
//        }
//
//        dialog.show()
//    }
//
//    private fun updatePermissionStatus() {
//        val micStatus = if (preferenceManager.hasMicrophonePermission(requireContext())) "허용됨" else "거부됨"
//        binding.tvMicStatus.text = micStatus
//        binding.tvMicStatus.setTextColor(
//            if (micStatus == "허용됨") ContextCompat.getColor(requireContext(), R.color.green)
//            else ContextCompat.getColor(requireContext(), R.color.red)
//        )
//
//        val notificationStatus = if (preferenceManager.hasNotificationPermission(requireContext())) "허용됨" else "거부됨"
//        binding.tvNotificationStatus.text = notificationStatus
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        _binding = null
//    }
//}