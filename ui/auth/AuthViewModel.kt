// ui/auth/AuthViewModel.kt
package com.example.rush_hz_plus.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rush_hz_plus.data.repository.UserProfileRepository
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val userProfileRepository: UserProfileRepository,
    private val database: DatabaseReference
) : ViewModel() {

    private val _loginState = MutableStateFlow<AuthState>(AuthState.Idle)
    val loginState: StateFlow<AuthState> = _loginState

    private val _registerState = MutableStateFlow<AuthState>(AuthState.Idle)
    val registerState: StateFlow<AuthState> = _registerState

    private val _authActionState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authActionState: StateFlow<AuthState> = _authActionState

    // =============== 로그인 ===============
    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = AuthState.Error("이메일과 비밀번호를 입력해주세요.")
            return
        }
        _loginState.value = AuthState.Loading
        viewModelScope.launch {
            runCatching {
                auth.signInWithEmailAndPassword(email, password)
            }.onSuccess {
                _loginState.value = AuthState.Success
            }.onFailure { e ->
                _loginState.value = AuthState.Error(parseAuthErrorMessage(e))
            }
        }
    }

    // =============== 회원가입 + 이메일 인증 ===============
    fun register(email: String, password: String, phone: String, role: String) {
        if (email.isBlank() || password.isBlank() || phone.isBlank()) {
            _registerState.value = AuthState.Error("모든 필드를 입력해주세요.")
            return
        }
        _registerState.value = AuthState.Loading
        viewModelScope.launch {
            runCatching {
                // await()로 Task<AuthResult>를 AuthResult로 변환
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val user = authResult.user
                if (user == null) {
                    throw IllegalStateException("사용자 생성 실패")
                }
                userProfileRepository.saveUserProfile(email, phone, role)
            }.onSuccess {
                _registerState.value = AuthState.Success
            }.onFailure { e ->
                _registerState.value = AuthState.Error(parseAuthErrorMessage(e))
            }
        }
    }

    fun sendEmailVerification() {
        val user = auth.currentUser
        if (user != null && !user.isEmailVerified) {
            viewModelScope.launch {
                runCatching {
                    user.sendEmailVerification().await()
                }.onSuccess {
                    _registerState.value = AuthState.EmailVerificationSent
                }.onFailure { e ->
                    _registerState.value = AuthState.Error("인증 이메일 발송 실패: ${e.message}")
                }
            }
        }
    }

    // =============== 로그아웃 ===============
    fun logout() {
        // Firebase 로그아웃
        auth.signOut()
    }

    // =============== 회원 탈퇴 (재인증 필요) ===============
    fun withdrawAccount(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authActionState.value = AuthState.Error("이메일과 비밀번호를 입력해주세요.")
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            _authActionState.value = AuthState.Error("로그인 상태가 아닙니다.")
            return
        }

        _authActionState.value = AuthState.Loading

        viewModelScope.launch {
            runCatching {
                // 1. 재인증
                val credential = EmailAuthProvider.getCredential(email, password)
                currentUser.reauthenticate(credential).await()

                // 2. 계정 삭제
                currentUser.delete().await()

                // 3. Realtime Database 데이터 삭제
                val uid = currentUser.uid
                database.child("users").child(uid).removeValue().await()

                // 4. 로그아웃 (실제로는 delete() 후 자동 로그아웃됨)
                auth.signOut()
            }.onSuccess {
                _authActionState.value = AuthState.WithdrawSuccess
            }.onFailure { e ->
                _authActionState.value = AuthState.Error("탈퇴 실패: ${parseAuthErrorMessage(e)}")
            }
        }
    }

    // =============== 유틸리티 ===============
    private fun parseAuthErrorMessage(e: Throwable): String {
        return when {
            e.message?.contains("ERROR_EMAIL_ALREADY_IN_USE", ignoreCase = true) == true ->
                "이미 사용 중인 이메일입니다."
            e.message?.contains("ERROR_INVALID_EMAIL", ignoreCase = true) == true ->
                "유효하지 않은 이메일입니다."
            e.message?.contains("ERROR_WEAK_PASSWORD", ignoreCase = true) == true ->
                "비밀번호는 6자 이상이어야 합니다."
            e.message?.contains("ERROR_USER_NOT_FOUND", ignoreCase = true) == true ->
                "존재하지 않는 계정입니다."
            e.message?.contains("ERROR_WRONG_PASSWORD", ignoreCase = true) == true ->
                "비밀번호가 일치하지 않습니다."
            else -> e.message ?: "알 수 없는 오류"
        }
    }

    // =============== 상태 정의 ===============
    sealed interface AuthState {
        object Idle : AuthState
        object Loading : AuthState
        object Success : AuthState
        object EmailVerificationSent : AuthState   // 인증 이메일 발송 완료
        object WithdrawSuccess : AuthState         // 탈퇴 성공
        data class Error(val message: String) : AuthState
    }
}