// core/utils/PreferenceManager.kt
package com.example.rush_hz_plus.core.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFERENCES_NAME = "app_settings"
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCES_NAME)

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // 키 정의
    private val SYSTEM_TTS_ENABLED = booleanPreferencesKey("system_tts_enabled")
    private val USER_TTS_ENABLED = booleanPreferencesKey("user_tts_enabled")
    private val USER_EMERGENCY_MESSAGE = stringPreferencesKey("user_emergency_message")
    private val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
    private val FLASH_ENABLED = booleanPreferencesKey("flash_enabled")
    private val AUTO_TTS_ON_L3 = booleanPreferencesKey("auto_tts_on_l3") // 추가

    // 기본값
    private val DEFAULT_SYSTEM_TTS = true
    private val DEFAULT_USER_TTS = true
    private val DEFAULT_USER_MESSAGE = "여기 도움이 필요합니다. 저는 청각장애인입니다."
    private val DEFAULT_VIBRATION = true
    private val DEFAULT_FLASH = true
    private val DEFAULT_AUTO_TTS_ON_L3 = false // 추가


}