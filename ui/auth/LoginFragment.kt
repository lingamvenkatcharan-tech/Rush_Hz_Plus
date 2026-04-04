// ui/auth/LoginFragment.kt
package com.example.rush_hz_plus.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.databinding.FragmentLoginBinding
import com.example.rush_hz_plus.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imageBack.setOnClickListener {
            activity?.onBackPressed()
        }

        binding.loginButton.setOnClickListener {
            val email = binding.inputEmail.text.toString().trim()
            val pw = binding.inputPw.text.toString().trim()
            viewModel.login(email, pw)
        }

        binding.signupButton.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_signup)
        }

        // 비밀번호 찾기: 실제 기능이 없으므로 토스트만 표시
        binding.tvForgotPassword.setOnClickListener {
            Toast.makeText(context, "비밀번호 재설정 기능은 추후 제공됩니다.", Toast.LENGTH_SHORT).show()
        }

        // 로그인 상태 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loginState.collect { state ->
                when (state) {
                    is AuthViewModel.AuthState.Idle -> Unit
                    is AuthViewModel.AuthState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.loginButton.isEnabled = false
                    }
                    is AuthViewModel.AuthState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.loginButton.isEnabled = true

                        handleLoginSuccess()
                    }
                    is AuthViewModel.AuthState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.loginButton.isEnabled = true
                        Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun handleLoginSuccess() {
        // SharedPreferences에 온보딩 완료 표시
        requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit()
            .putBoolean("onboarding_completed", true)
            .apply()

        // AuthActivity 종료 + MainActivity 시작
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().finish() // AuthActivity 종료
    }
}