// core/di/DataModule.kt
package com.example.rush_hz_plus.core.di

import android.content.Context
import androidx.room.Room
import com.example.rush_hz_plus.data.local.AppDatabase
import com.example.rush_hz_plus.data.local.dao.DetectionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "rush_hz_plus_database"
        )
            .fallbackToDestructiveMigration() // 개발 중에는 기존 DB 삭제 허용
            .build()
    }

    @Provides
    @Singleton
    fun provideDetectionDao(appDatabase: AppDatabase): DetectionDao {
        return appDatabase.detectionDao()
    }

}