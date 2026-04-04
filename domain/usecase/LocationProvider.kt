package com.example.rush_hz_plus.domain.usecase

interface LocationProvider {
    suspend fun getCurrentLocation(): Pair<Double, Double>    // 현재 위치
    suspend fun getLastKnownLocation(): Pair<Double, Double>? // 마지막 위치
}