// data/repository/LocationProviderImpl.kt
package com.example.rush_hz_plus.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.rush_hz_plus.core.utils.Logger
import com.example.rush_hz_plus.domain.usecase.LocationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationProvider {

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val defaultLocation = 37.5665 to 126.9780

    override suspend fun getCurrentLocation(): Pair<Double, Double> {
        return getLocationOrNull() ?: defaultLocation
    }

    override suspend fun getLastKnownLocation(): Pair<Double, Double>? {
        return getLocationOrNull()
    }

    private suspend fun getLocationOrNull(): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Logger.w("LocationProvider", "위치 권한 없음")
            return null
        }

        return suspendCancellableCoroutine { cont ->
            val task = fusedLocationClient.lastLocation
            cont.invokeOnCancellation {
                // Task 취소는 Android Location API에서 직접 지원하지 않음
                // 대신 코루틴 취소 시 콜백 무시
            }

            task.addOnCompleteListener { completedTask ->
                if (cont.isCompleted) return@addOnCompleteListener // 이미 취소됨

                if (completedTask.isSuccessful && completedTask.result != null) {
                    val location = completedTask.result
                    cont.resume(location.latitude to location.longitude)
                } else {
                    cont.resume(null)
                }
            }
        }
    }
}