// data/local/converter/Converters.kt
package com.example.rush_hz_plus.data.local.converter

import androidx.room.TypeConverter

object Converters {

    @TypeConverter
    fun fromFloatArray(array: FloatArray?): String {
        return array?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toFloatArray(value: String?): FloatArray {
        return value?.split(",")?.mapNotNull {
            it.toFloatOrNull()
        }?.toFloatArray() ?: floatArrayOf()
    }
}