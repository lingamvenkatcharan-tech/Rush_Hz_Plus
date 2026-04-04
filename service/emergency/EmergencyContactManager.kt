// service/emergency/EmergencyContactManager.kt
package com.example.rush_hz_plus.service.emergency

import android.content.Context
import com.example.rush_hz_plus.domain.usecase.UserIdProvider
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class EmergencyContactManager @Inject constructor(
    private val database: DatabaseReference,
    private val userIdProvider: UserIdProvider
) {

    suspend fun getGuardiansOfCurrentUser(): List<GuardianInfo> {
        val userId = userIdProvider.getCurrentUserId()
        if (userId == "anonymous") return emptyList()

        val guardiansNode = database.child("users").child(userId).child("guardians").get().await()
        if (!guardiansNode.exists()) return emptyList()

        val guardianList = mutableListOf<GuardianInfo>()

        guardiansNode.children.forEach { child ->
            val data = child.value as? Map<*, *> ?: return@forEach
            val isAppUser = data["isAppUser"] as? Boolean ?: false
            val phone = data["phone"] as? String ?: ""

            if (isAppUser) {
                // 앱 사용자: 키 = UID
                val guardianUid = child.key ?: return@forEach
                val guardianSnapshot = database.child("users").child(guardianUid).get().await()
                if (guardianSnapshot.exists()) {
                    guardianList.add(
                        GuardianInfo(
                            uid = guardianUid,
                            phoneNumber = guardianSnapshot.child("phone").value?.toString() ?: phone,
                            nickname = guardianSnapshot.child("nickname").value?.toString() ?: "보호자",
                            isAppUser = true
                        )
                    )
                }
            } else {
                // 비앱 사용자
                guardianList.add(
                    GuardianInfo(
                        uid = null,
                        phoneNumber = phone,
                        nickname = data["nickname"] as? String ?: "보호자",
                        isAppUser = false
                    )
                )
            }
        }

        return guardianList
    }

}

data class GuardianInfo(
    val uid: String?,          // 앱 사용자일 때만 존재
    val phoneNumber: String,
    val nickname: String,
    val isAppUser: Boolean
)