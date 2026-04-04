// ui/menu/UserMenuFragment.kt
package com.example.rush_hz_plus.ui.menu


import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.databinding.FragmentUserMenuBinding
import com.example.rush_hz_plus.ui.auth.AuthActivity
import com.example.rush_hz_plus.ui.auth.AuthViewModel
import com.example.rush_hz_plus.ui.main.MainActivity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class UserMenuFragment : Fragment() {

    private var _binding: FragmentUserMenuBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by activityViewModels()
    private val profileViewModel: ProfileViewModel by activityViewModels()
    private val firebaseAuth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 뒤로가기
        binding.imageBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 메뉴 항목 이동
        binding.menuMyInfo.setOnClickListener {
            findNavController().navigate(R.id.action_userMenu_to_myInfo)
        }
        binding.menuVibrationExample.setOnClickListener {
            findNavController().navigate(R.id.action_userMenu_to_vibrationDemo)
        }
        binding.menuFlashExample.setOnClickListener {
            findNavController().navigate(R.id.action_userMenu_to_flashDemo)
        }
        binding.menuEmergencyContact.setOnClickListener {
            findNavController().navigate(R.id.action_userMenu_to_emergencyContact)
        }
        binding.menuAutoMessage.setOnClickListener {
            findNavController().navigate(R.id.action_userMenu_to_autoMessageSetting)
        }
        binding.menuServiceInfo.setOnClickListener {
            findNavController().navigate(R.id.action_userMenu_to_serviceInfo)
        }

        // 로그인 버튼 클릭 시 AuthActivity로 이동
        binding.loginButton.setOnClickListener {
            val intent = Intent(requireContext(), AuthActivity::class.java)
            startActivity(intent)
        }

        // 로그아웃 버튼 클릭 시 MainActivity로 이동
        binding.logoutButton.setOnClickListener {
            performLogout()
        }

        // 로그인 상태 반영
        updateLoginLogoutUI()
        observeProfileData()
    }

    /** 프로필 LiveData 관찰 */
    private fun observeProfileData() {
        profileViewModel.userType.observe(viewLifecycleOwner) { type ->
            binding.textUserType.text = type ?: "정보 없음"
        }
        profileViewModel.userId.observe(viewLifecycleOwner) { id ->
            binding.userNameText.text = if (!id.isNullOrBlank()) "$id 님" else "회원 님"
        }
    }

    /** 로그인 / 로그아웃 상태에 따라 버튼 전환 */
    private fun updateLoginLogoutUI() {
        val isLoggedIn = firebaseAuth.currentUser != null
        binding.logoutButton.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
        binding.loginButton.visibility = if (isLoggedIn) View.GONE else View.VISIBLE

        if (!isLoggedIn) {
            binding.textUserType.text = "로그인이 필요합니다"
            binding.userNameText.text = "비회원"
        }
    }

    /** 로그아웃 처리 */
    private fun performLogout() {
        authViewModel.logout()
        profileViewModel.clearProfileData()

        // MainActivity로 이동
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
