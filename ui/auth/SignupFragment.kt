// ui/auth/SignupFragment.kt
package com.example.rush_hz_plus.ui.auth

import android.app.Dialog
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
import com.example.rush_hz_plus.databinding.DialogEmailVerificationSentBinding
import com.example.rush_hz_plus.databinding.FragmentSignupBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class SignupFragment : Fragment() {

    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imageBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.userButton.isChecked = true

        binding.signupButton.setOnClickListener {
            val email = binding.inputEmail.text.toString().trim()
            val pw = binding.inputPw.text.toString().trim()
            val phone = binding.inputPhone.text.toString().trim()
            val role = if (binding.userButton.isChecked) "user" else "guardian"
            viewModel.register(email, pw, phone, role)
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.registerState.collect { state ->
                when (state) {
                    is AuthViewModel.AuthState.Idle -> Unit
                    is AuthViewModel.AuthState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.signupButton.isEnabled = false
                    }
                    is AuthViewModel.AuthState.Success -> {
                        // 이메일 인증 요청
                        viewModel.sendEmailVerification()
                    }
                    is AuthViewModel.AuthState.EmailVerificationSent -> {
                        // 이메일 인증 완료 → 로그인 화면으로 자동 이동
                        binding.progressBar.visibility = View.GONE
                        binding.signupButton.isEnabled = true
                        findNavController().popBackStack(R.id.loginFragment, false)
                    }
                    is AuthViewModel.AuthState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.signupButton.isEnabled = true
                        Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        Timber.d("Auth", "Ignored state: $state")
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}