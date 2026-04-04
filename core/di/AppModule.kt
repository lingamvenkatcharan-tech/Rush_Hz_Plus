package com.example.rush_hz_plus.core.di

import android.content.Context
import android.content.res.AssetManager
import android.net.ConnectivityManager
import com.example.rush_hz_plus.core.utils.PreferenceManager
import com.example.rush_hz_plus.data.local.AppDatabase
import com.example.rush_hz_plus.data.repository.DetectionRepositoryImpl
import com.example.rush_hz_plus.data.repository.DetectionRepositoryInterface
import com.example.rush_hz_plus.data.repository.FirebaseUserProvider
import com.example.rush_hz_plus.data.repository.FirebaseUserProviderImpl
import com.example.rush_hz_plus.data.repository.LocationProviderImpl
import com.example.rush_hz_plus.data.repository.UserIdProviderImpl
import com.example.rush_hz_plus.data.repository.UserProfileRepository
import com.example.rush_hz_plus.data.repository.UserProfileRepositoryImpl
import com.example.rush_hz_plus.domain.usecase.LocationProvider
import com.example.rush_hz_plus.domain.usecase.UserIdProvider
import com.example.rush_hz_plus.model.TFLiteInterpreter
import com.example.rush_hz_plus.service.alert.AlertManager
import com.example.rush_hz_plus.service.alert.FlashAlert
import com.example.rush_hz_plus.service.alert.NotificationHelper
import com.example.rush_hz_plus.service.alert.TTSManager
import com.example.rush_hz_plus.service.alert.VibrationAlert
import com.example.rush_hz_plus.service.emergency.EmergencyContactManager
import com.example.rush_hz_plus.service.emergency.EmergencyManager
import com.example.rush_hz_plus.service.emergency.SMSHelper
import com.example.rush_hz_plus.service.system.ForegroundServiceManager
import com.example.rush_hz_plus.service.system.PermissionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun providePreferenceManager(@ApplicationContext context: Context): PreferenceManager {
        return PreferenceManager(context)
    }

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): DatabaseReference {
        return FirebaseDatabase.getInstance().reference
    }

    @Provides
    @Singleton
    fun provideDetectionRepository(
        @ApplicationContext context: Context,
        appDatabase: AppDatabase,
        logInfoRef: DatabaseReference,
        connectivityManager: ConnectivityManager
    ): DetectionRepositoryInterface {
        return DetectionRepositoryImpl(context, appDatabase, logInfoRef, connectivityManager)
    }

    @Provides
    @Singleton
    fun provideConnectivityManager(@ApplicationContext context: Context): ConnectivityManager {
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseUserProvider(
        firebaseAuth: FirebaseAuth
    ): FirebaseUserProvider {
        return FirebaseUserProviderImpl(firebaseAuth)
    }

    @Provides
    @Singleton
    fun provideUserIdProvider(
        firebaseUserProvider: FirebaseUserProvider
    ): UserIdProvider {
        return UserIdProviderImpl(firebaseUserProvider)
    }

    @Provides
    @Singleton
    fun provideUserProfileRepository(
        database: DatabaseReference,
        userIdProvider: UserIdProvider
    ): UserProfileRepository {
        return UserProfileRepositoryImpl(database, userIdProvider)
    }

    @Provides
    @Singleton
    fun provideLocationProvider(
        @ApplicationContext context: Context
    ): LocationProvider {
        return LocationProviderImpl(context)
    }

    // === TFLite 및 알림 시스템 (전역 싱글턴) ===
    @Provides
    @Singleton
    fun provideAssetManager(@ApplicationContext context: Context): AssetManager {
        return context.assets
    }

    @Provides
    @Singleton
    fun provideTFLiteInterpreter(@ApplicationContext context: Context): TFLiteInterpreter {
        return TFLiteInterpreter(context)
    }

    @Provides
    @Singleton
    fun provideTtsManager(@ApplicationContext context: Context): TTSManager {
        return TTSManager(context)
    }

    @Provides
    @Singleton
    fun provideVibrationAlert(@ApplicationContext context: Context, permissionManager: PermissionManager): VibrationAlert {
        return VibrationAlert(context, permissionManager)
    }

    @Provides
    @Singleton
    fun provideFlashAlert(@ApplicationContext context: Context): FlashAlert {
        return FlashAlert(context)
    }

    @Provides
    @Singleton
    fun provideNotificationHelper(@ApplicationContext context: Context, permissionManager: PermissionManager): NotificationHelper {
        return NotificationHelper(context, permissionManager)
    }

    @Provides
    @Singleton
    fun provideAlertManager(
        @ApplicationContext context: Context,
        flashAlert: FlashAlert,
        vibrationAlert: VibrationAlert,
        ttsManager: TTSManager,
        notificationHelper: NotificationHelper,
        preferenceManager: PreferenceManager,
        detectionRepository: DetectionRepositoryInterface
    ): AlertManager {
        return AlertManager(context, flashAlert, vibrationAlert, ttsManager, notificationHelper, preferenceManager, detectionRepository)
    }

    @Provides
    @Singleton
    fun provideSMSHelper(@ApplicationContext context: Context): SMSHelper {
        return SMSHelper(context)
    }

    @Provides
    @Singleton
    fun provideEmergencyContactManager(
        database: DatabaseReference,
        userIdProvider: UserIdProvider
    ): EmergencyContactManager {
        return EmergencyContactManager(database, userIdProvider)
    }

    @Provides
    @Singleton
    fun provideEmergencyManager(
        @ApplicationContext context: Context,
        database: DatabaseReference,
        contactManager: EmergencyContactManager,
        smsHelper: SMSHelper,
        locationProvider: LocationProvider,
        userIdProvider: UserIdProvider,
        alertManager: AlertManager,
        foregroundServiceManager: ForegroundServiceManager,
        ttsManager: TTSManager,
        appScope: CoroutineScope,
        detectionRepository: DetectionRepositoryInterface
    ): EmergencyManager {
        return EmergencyManager(context, database, contactManager, smsHelper, locationProvider, userIdProvider, alertManager, foregroundServiceManager, ttsManager, appScope, detectionRepository)
    }
}