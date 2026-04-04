package com.example.rush_hz_plus.data.repository

import com.example.rush_hz_plus.domain.usecase.UserIdProvider
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface UserProfileRepository {
    suspend fun saveUserProfile(email: String, phone: String, role: String)
    suspend fun getProfile(): Map<String, Any?>?
    suspend fun addGuardian(phone: String, nickname: String)
    suspend fun getUserRole(): String
}

@Singleton
class UserProfileRepositoryImpl @Inject constructor(
    private val database: DatabaseReference,
    private val userIdProvider: UserIdProvider
) : UserProfileRepository {

    override suspend fun saveUserProfile(email: String, phone: String, role: String) {
        val userId = userIdProvider.getCurrentUserId()
        if (userId == "알 수 없는 사용자") throw IllegalStateException("로그인 상태가 아닙니다.")

        val userProfile = mapOf(
            "email" to email,
            "nickname" to email.substringBefore("@"),
            "phone" to phone,
            "role" to role,
            "user_id" to userId
        )

        database.child("users").child(userId).setValue(userProfile).await()
    }

    override suspend fun getProfile(): Map<String, Any?>? {
        val userId = userIdProvider.getCurrentUserId()
        if (userId == "알 수 없는 사용자") return null
        return database.child("users").child(userId).get().await().value as? Map<String, Any?>
    }

    override suspend fun addGuardian(phone: String, nickname: String) {
        val userId = userIdProvider.getCurrentUserId()
        if (userId == "알 수 없는 사용자") throw IllegalStateException("로그인 상태가 아닙니다.")

        val role = database.child("users").child(userId).child("role").get().await().value as? String
        if (role != "user") throw SecurityException("보호자 등록은 'user'만 가능합니다.")

        // 입력 번호 정규화
        val normalizedInput = normalizePhone(phone)

        val allUsers = database.child("users").get().await().value as? Map<String, Any?> ?: emptyMap()
        var matchedGuardianUid: String? = null

        for ((uid, data) in allUsers) {
            val phoneInDb = (data as? Map<*, *>)?.get("phone") as? String
            if (phoneInDb != null && normalizePhone(phoneInDb) == normalizedInput) {
                matchedGuardianUid = uid
                break
            }
        }

        val isAppUser = matchedGuardianUid != null
        val guardianData = mapOf(
            "phone" to phone,          // 원본 저장 (UI 표시용)
            "nickname" to nickname,
            "isAppUser" to isAppUser
        )

        // 키도 정규화된 번호 사용 (비앱 사용자)
        val key = if (isAppUser) {
            matchedGuardianUid!!
        } else {
            normalizedInput // "01012345678" 형식
        }

        database.child("users").child(userId)
            .child("guardians").child(key)
            .setValue(guardianData)
            .await()
    }

    private fun normalizePhone(phone: String): String {
        return phone.replace(Regex("[^0-9]"), "")
            .let { num ->
                when {
                    num.startsWith("82") && num.length == 12 -> num.substring(2) // 8210... → 010...
                    num.startsWith("0") && num.length in 10..11 -> num
                    num.length in 9..10 -> "0$num" // 1012345678 → 01012345678
                    else -> num // 그대로 (비교용)
                }
            }
    }

    override suspend fun getUserRole(): String {
        val userId = userIdProvider.getCurrentUserId()
        if (userId == "알 수 없는 사용자") return "user" // 익명 사용자는 일반 사용자로 간주

        return try {
            val snapshot = database.child("users").child(userId).child("role").get().await()
            snapshot.value?.toString() ?: "user"
        } catch (e: Exception) {
            Timber.e(e, "사용자 역할 조회 실패 → 기본값 'user' 사용")
            "user"
        }
    }
}
