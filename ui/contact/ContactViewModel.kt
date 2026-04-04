package com.example.rush_hz_plus.ui.contact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rush_hz_plus.data.repository.UserProfileRepository
import com.google.firebase.database.DatabaseReference
import com.example.rush_hz_plus.domain.usecase.UserIdProvider
import com.example.rush_hz_plus.service.emergency.EmergencyContactManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ContactViewModel @Inject constructor(
    private val database: DatabaseReference,
    private val userIdProvider: UserIdProvider,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _guardians = MutableStateFlow<List<GuardianDisplayInfo>>(emptyList())
    val guardians: StateFlow<List<GuardianDisplayInfo>> = _guardians

    private val _operationState = MutableStateFlow("")
    val operationState: StateFlow<String> = _operationState

    private val _messages = MutableStateFlow<List<AutoMessage>>(emptyList())
    val messages: StateFlow<List<AutoMessage>> = _messages

    // =================== 보호자 ===================

    fun loadGuardians() {
        viewModelScope.launch {
            val userId = userIdProvider.getCurrentUserId()
            if (userId == "anonymous") return@launch

            val snapshot = database.child("users").child(userId).child("guardians").get().await()
            val list = snapshot.children.mapNotNull { child ->
                val data = child.value as? Map<*, *> ?: return@mapNotNull null
                GuardianDisplayInfo(
                    uid = child.key ?: return@mapNotNull null,
                    phoneNumber = data["phone"] as? String ?: "",
                    nickname = data["nickname"] as? String ?: "보호자",
                    isAppUser = data["isAppUser"] as? Boolean ?: false
                )
            }
            _guardians.value = list
        }
    }

    fun addGuardian(name: String, phone: String) {
        viewModelScope.launch {
            try {
                // UserProfileRepository의 완전한 로직 재사용
                userProfileRepository.addGuardian(phone = phone, nickname = name)
                _operationState.value = "$name 님 추가 완료"
                loadGuardians()
            } catch (e: Exception) {
                Timber.e(e, "보호자 추가 실패")
                _operationState.value = "추가 실패: ${e.message}"
            }
        }
    }

    // 삭제/수정은 기존대로 (키 기반)
    fun removeGuardian(guardian: GuardianDisplayInfo) {
        viewModelScope.launch {
            try {
                val userId = userIdProvider.getCurrentUserId()
                if (userId == "anonymous") return@launch

                guardian.uid?.let {
                    database.child("users").child(userId).child("guardians")
                        .child(it) // uid = 키 (UID 또는 전화번호)
                        .removeValue()
                        .await()
                }

                _operationState.value = "${guardian.nickname} 님 삭제 완료"
                loadGuardians()
            } catch (e: Exception) {
                _operationState.value = "삭제 실패: ${e.message}"
            }
        }
    }

    // 수정 시: 기존 키 유지 + 데이터 덮어쓰기
    fun editGuardian(oldUid: String, newNickname: String, newPhone: String) {
        viewModelScope.launch {
            try {
                val userId = userIdProvider.getCurrentUserId()
                if (userId == "anonymous") return@launch

                // 1. 먼저 기존 보호자 정보 로드 → isAppUser 확인
                val oldSnapshot = database.child("users").child(userId).child("guardians").child(oldUid).get().await()
                val oldData = oldSnapshot.value as? Map<*, *> ?: return@launch
                val wasAppUser = oldData["isAppUser"] as? Boolean ?: false

                // 2. 새 전화번호로 앱 사용자 재확인
                val allUsers = database.child("users").get().await().value as? Map<String, Any?> ?: emptyMap()
                var matchedGuardianUid: String? = null
                for ((uid, data) in allUsers) {
                    val phoneInDb = (data as? Map<*, *>)?.get("phone") as? String
                    if (phoneInDb == newPhone) {
                        matchedGuardianUid = uid
                        break
                    }
                }
                val isNowAppUser = matchedGuardianUid != null

                // 3. 키 결정
                val newKey = if (isNowAppUser) {
                    matchedGuardianUid!!
                } else {
                    newPhone
                }

                val newData = mapOf(
                    "nickname" to newNickname,
                    "phone" to newPhone,
                    "isAppUser" to isNowAppUser
                )

                val guardiansRef = database.child("users").child(userId).child("guardians")

                // 4. 키 변경 여부에 따라 처리
                if (newKey == oldUid) {
                    // 키 동일 → 덮어쓰기
                    guardiansRef.child(newKey).setValue(newData).await()
                } else {
                    // 키 변경 → 기존 삭제 + 새 항목 생성
                    guardiansRef.child(oldUid).removeValue().await()
                    guardiansRef.child(newKey).setValue(newData).await()
                }

                _operationState.value = "$newNickname 님 정보 수정 완료"
                loadGuardians()
            } catch (e: Exception) {
                Timber.e(e, "보호자 수정 실패")
                _operationState.value = "수정 실패: ${e.message}"
            }
        }
    }

    // =================== 자동 메시지 ===================

    fun loadMessages() {
        viewModelScope.launch {
            val userId = userIdProvider.getCurrentUserId()
            if (userId == "anonymous") return@launch

            val snapshot = database.child("users").child(userId).child("auto_messages").get().await()
            val list = snapshot.children.mapNotNull { child ->
                AutoMessage(
                    id = child.key ?: "",
                    content = child.child("content").value?.toString() ?: ""
                )
            }
            _messages.value = list
        }
    }

    fun addMessage(content: String) {
        viewModelScope.launch {
            try {
                val userId = userIdProvider.getCurrentUserId()
                if (userId == "anonymous") return@launch

                val key = database.child("users").child(userId).child("auto_messages").push().key ?: return@launch
                val data = mapOf("content" to content)
                database.child("users").child(userId).child("auto_messages").child(key).setValue(data).await()

                _operationState.value = "메시지 추가 완료"
                loadMessages()
            } catch (e: Exception) {
                _operationState.value = "메시지 추가 실패: ${e.message}"
            }
        }
    }

    fun removeMessage(message: AutoMessage) {
        viewModelScope.launch {
            try {
                val userId = userIdProvider.getCurrentUserId()
                if (userId == "anonymous") return@launch

                database.child("users").child(userId).child("auto_messages")
                    .child(message.id)
                    .removeValue()
                    .await()

                _operationState.value = "메시지 삭제 완료"
                loadMessages()
            } catch (e: Exception) {
                _operationState.value = "메시지 삭제 실패: ${e.message}"
            }
        }
    }

    // 수정: 메시지 내용을 외부로 전달
    fun editMessage(message: AutoMessage) {
        _operationState.value = "메시지 수정 모드 진입"
        // 또는 _editMessageEvent.tryEmit(message)
    }
}

data class AutoMessage(
    val id: String,
    val content: String
)
