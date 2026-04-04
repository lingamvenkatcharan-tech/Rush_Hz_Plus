package com.example.rush_hz_plus.ui.menu

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.databinding.FragmentMyInfoBinding
import com.example.rush_hz_plus.ui.auth.AuthViewModel
import com.example.rush_hz_plus.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MyInfoFragment : Fragment() {

    private var _binding: FragmentMyInfoBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by activityViewModels()
    private val profileViewModel: ProfileViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🔙 뒤로가기
        binding.imageBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 👤 프로필 정보 표시
        profileViewModel.email.observe(viewLifecycleOwner) { email ->
            binding.tvEmail.text = "이메일: $email"
        }
        profileViewModel.phone.observe(viewLifecycleOwner) { phone ->
            binding.tvPhoneNumber.text = "전화번호: $phone"
        }

        // ✏️ 수정 버튼
        binding.btnEdit.setOnClickListener {
            showEditDialog()
        }

        // 🚪 탈퇴 버튼
        binding.btnWithdraw.setOnClickListener {
            showWithdrawDialog()
        }

        // 🔁 회원정보 수정 결과 관찰
        profileViewModel.updateResult.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { success ->
                if (success) {
                    Toast.makeText(requireContext(), "회원 정보가 수정되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "수정 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 👀 탈퇴 결과 처리
        viewLifecycleOwner.lifecycleScope.launch {
            authViewModel.authActionState.collect { state ->
                when (state) {
                    is AuthViewModel.AuthState.Loading -> {
                        // 로딩 UI (필요시 ProgressBar 표시)
                    }
                    is AuthViewModel.AuthState.WithdrawSuccess -> {
                        handleWithdrawSuccess()
                    }
                    is AuthViewModel.AuthState.Error -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> Unit
                }
            }
        }
    }

    /**
     * 이메일 / 전화번호 수정 다이얼로그
     */
    private fun showEditDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_profile, null)

        val etEmail = dialogView.findViewById<EditText>(R.id.et_email)
        val etPhone = dialogView.findViewById<EditText>(R.id.et_phone)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save)

        // 현재 값 미리 표시
        etEmail.setText(profileViewModel.email.value ?: "")
        etPhone.setText(profileViewModel.phone.value ?: "")

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val newEmail = etEmail.text.toString().trim()
            val newPhone = etPhone.text.toString().trim()

            if (newEmail.isBlank() || newPhone.isBlank()) {
                Toast.makeText(requireContext(), "이메일과 전화번호를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            profileViewModel.updateProfile(newEmail, newPhone)
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * 회원 탈퇴 다이얼로그
     */
    private fun showWithdrawDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_withdraw, null)

        val etEmail = dialogView.findViewById<EditText>(R.id.et_email)
        val etPassword = dialogView.findViewById<EditText>(R.id.et_password)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btn_confirm_withdraw)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(requireContext(), "이메일과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            authViewModel.withdrawAccount(email, password)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun handleWithdrawSuccess() {
        profileViewModel.clearProfileData()
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
