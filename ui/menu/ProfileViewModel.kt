package com.example.rush_hz_plus.ui.menu

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.example.rush_hz_plus.core.utils.Event
import com.example.rush_hz_plus.data.repository.UserProfileRepository
import com.example.rush_hz_plus.domain.usecase.UserIdProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    application: Application,
    private val userProfileRepository: UserProfileRepository,
    private val database: DatabaseReference,
    private val userIdProvider: UserIdProvider
) : AndroidViewModel(application) {

    companion object {
        const val PREFS_NAME = "user_prefs"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_TYPE = "user_type"
        const val KEY_EMAIL = "email"
        const val KEY_PHONE = "phone_number"
        const val DEFAULT_USER_ID = "회원"
    }

    private val _userId = MutableLiveData<String>()
    val userId: LiveData<String> = _userId

    private val _userType = MutableLiveData<String>()
    val userType: LiveData<String> = _userType

    private val _email = MutableLiveData<String>()
    val email: LiveData<String> = _email

    private val _phone = MutableLiveData<String>()
    val phone: LiveData<String> = _phone

    private val _updateResult = MutableLiveData<Event<Boolean>>()
    val updateResult: LiveData<Event<Boolean>> = _updateResult

    private val auth = FirebaseAuth.getInstance()
    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) loadProfileFromFirebase() else clearProfileData()
    }

    init {
        auth.addAuthStateListener(authStateListener)
        if (auth.currentUser != null) loadProfileFromFirebase() else loadProfileFromPrefs()
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
    }

    private fun loadProfileFromPrefs() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _userId.value = prefs.getString(KEY_USER_ID, DEFAULT_USER_ID)
        _userType.value = prefs.getString(KEY_USER_TYPE, "정보 없음")
        _email.value = prefs.getString(KEY_EMAIL, "이메일 정보 없음")
        _phone.value = prefs.getString(KEY_PHONE, "전화번호 정보 없음")
    }

    private fun saveProfileToPrefs(email: String, phone: String) {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_EMAIL, email).putString(KEY_PHONE, phone).apply()
        _email.value = email
        _phone.value = phone
    }

    private fun loadProfileFromFirebase() {
        viewModelScope.launch {
            try {
                val profile = userProfileRepository.getProfile()
                if (profile != null) {
                    val email = profile["email"] as? String ?: "이메일 정보 없음"
                    val phone = profile["phone"] as? String ?: "전화번호 정보 없음"
                    val role = profile["role"] as? String ?: "정보 없음"
                    val uid = profile["user_id"] as? String ?: userIdProvider.getCurrentUserId()

                    val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString(KEY_EMAIL, email)
                        .putString(KEY_PHONE, phone)
                        .putString(KEY_USER_TYPE, role)
                        .putString(KEY_USER_ID, uid)
                        .apply()

                    _email.postValue(email)
                    _phone.postValue(phone)
                    _userType.postValue(role)
                    _userId.postValue(uid)
                } else loadProfileFromPrefs()
            } catch (e: Exception) {
                loadProfileFromPrefs()
            }
        }
    }

    /**
     * 이메일/전화번호 수정
     * 보호자의 경우 → 등록된 모든 피보호자 계정에서도 반영
     */
    fun updateProfile(newEmail: String, newPhone: String) {
        val userId = userIdProvider.getCurrentUserId()
        if (userId == "알 수 없는 사용자") {
            _updateResult.postValue(Event(false))
            return
        }

        viewModelScope.launch {
            try {
                val updates = mapOf(
                    "email" to newEmail,
                    "phone" to newPhone
                )

                // 1️⃣ 현재 사용자 정보 수정
                database.child("users").child(userId).updateChildren(updates).await()

                // 2️⃣ SharedPreferences & LiveData 갱신
                saveProfileToPrefs(newEmail, newPhone)

                // 3️⃣ 보호자인 경우 피보호자 데이터도 수정
                val currentRole = database.child("users").child(userId).child("role").get().await().value as? String
                if (currentRole == "guardian") {
                    propagateGuardianUpdate(userId, newEmail, newPhone)
                }

                _updateResult.postValue(Event(true))
            } catch (e: Exception) {
                _updateResult.postValue(Event(false))
            }
        }
    }

    /**
     * 보호자의 이메일/전화번호 변경 시
     * 자신이 등록된 모든 피보호자(users/{childId}/guardians/{phone})에도 반영
     */
    private suspend fun propagateGuardianUpdate(guardianUid: String, newEmail: String, newPhone: String) {
        try {
            val allUsers = database.child("users").get().await().value as? Map<String, Any?> ?: return

            for ((childUid, childData) in allUsers) {
                val guardians = (childData as? Map<*, *>)?.get("guardians") as? Map<String, Any?> ?: continue

                for ((guardianPhone, guardianInfo) in guardians) {
                    val guardianUidInChild = (guardianInfo as? Map<*, *>)?.get("guardianUid") as? String
                    if (guardianUidInChild == guardianUid) {
                        // 보호자 정보 업데이트
                        val updatedGuardianData = mutableMapOf<String, Any?>(
                            "nickname" to guardianInfo["nickname"],
                            "isAppUser" to true,
                            "guardianUid" to guardianUid,
                            "email" to newEmail,
                            "phone" to newPhone
                        )

                        database.child("users")
                            .child(childUid)
                            .child("guardians")
                            .child(newPhone)
                            .setValue(updatedGuardianData)
                            .await()

                        // 이전 전화번호 key와 다르면 이전 key는 삭제
                        if (guardianPhone != newPhone) {
                            database.child("users")
                                .child(childUid)
                                .child("guardians")
                                .child(guardianPhone)
                                .removeValue()
                                .await()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 보호자 동기화 실패 시 무시 (재시도 로직은 필요 시 추가)
        }
    }

    fun clearProfileData() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        _userId.postValue(DEFAULT_USER_ID)
        _userType.postValue("정보 없음")
        _email.postValue("이메일 정보 없음")
        _phone.postValue("전화번호 정보 없음")
    }
}
