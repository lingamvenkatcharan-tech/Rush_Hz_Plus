// core/di/ServiceModule.kt
package com.example.rush_hz_plus.core.di


import android.content.Context
import com.example.rush_hz_plus.data.repository.DetectionRepositoryImpl
import com.example.rush_hz_plus.data.repository.DetectionRepositoryInterface
import com.example.rush_hz_plus.domain.score.KeywordDetector
import com.example.rush_hz_plus.domain.usecase.LocationProvider
import com.example.rush_hz_plus.domain.usecase.ProcessAudioUseCase
import com.example.rush_hz_plus.domain.usecase.UserIdProvider
import com.example.rush_hz_plus.model.TFLiteInterpreter
import com.example.rush_hz_plus.service.alert.*
import com.example.rush_hz_plus.service.emergency.EmergencyManager
import com.example.rush_hz_plus.service.monitor.InferenceEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.CoroutineScope


@Module
@InstallIn(ServiceComponent::class)
object ServiceModule {

    @JvmStatic
    @Provides
    @ServiceScoped
    fun provideInferenceEngine(
        tfliteInterpreter: TFLiteInterpreter, // ← Singleton에서 제공됨
        keywordDetector: KeywordDetector,
    ): InferenceEngine {
        return InferenceEngine(tfliteInterpreter, keywordDetector)
    }

    @JvmStatic
    @Provides
    @ServiceScoped
    fun provideProcessAudioUseCase(
        inferenceEngine: InferenceEngine,
        alertManager: AlertManager, // ← Singleton에서 제공됨
        emergencyManager: EmergencyManager,
        repository: DetectionRepositoryInterface,
        locationProvider: LocationProvider,
        userIdProvider: UserIdProvider,
        appScope: CoroutineScope,
        @ApplicationContext context: Context
    ): ProcessAudioUseCase {
        return ProcessAudioUseCase(inferenceEngine, alertManager, emergencyManager, repository, locationProvider, userIdProvider, appScope, context)
    }

}