// domain/usecase/UserIdProvider.kt
package com.example.rush_hz_plus.domain.usecase

interface UserIdProvider {
    fun getCurrentUserId(): String
}