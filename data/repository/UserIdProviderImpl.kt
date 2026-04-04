// data/repository/UserIdProviderImpl.kt
package com.example.rush_hz_plus.data.repository

import com.example.rush_hz_plus.domain.usecase.UserIdProvider
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton

// 인터페이스 기반 설계
interface FirebaseUserProvider {
    val currentUser: com.google.firebase.auth.FirebaseUser?
}

@Singleton
class FirebaseUserProviderImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : FirebaseUserProvider {
    override val currentUser: com.google.firebase.auth.FirebaseUser?
        get() = firebaseAuth.currentUser
}

@Singleton
class UserIdProviderImpl @Inject constructor(
    private val firebaseUserProvider: FirebaseUserProvider
) : UserIdProvider {

    override fun getCurrentUserId(): String {
        return firebaseUserProvider.currentUser?.uid ?: "알 수 없는 사용자"
    }
}