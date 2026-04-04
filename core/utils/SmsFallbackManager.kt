// core/utils/SmsFallbackManager.kt
package com.example.rush_hz_plus.core.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import timber.log.Timber

object SmsFallbackManager {

    /**
     * SMS 앱 위임 실행 — 성공 여부(Boolean) 반환
     */
    fun sendSmsFallback(
        context: Context,
        phoneNumber: String,
        message: String,
        detectionId: Long,
        callback: (isSuccess: Boolean, type: String) -> Unit
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber")).apply {
                putExtra("sms_body", message)
                setPackage(Telephony.Sms.getDefaultSmsPackage(context))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val canResolve = intent.resolveActivity(context.packageManager) != null
            if (canResolve) {
                context.startActivity(intent)
                callback(true, "SMS_DELEGATED")
                true
            } else {
                // fallback without package restriction
                intent.`package` = null
                val genericResolve = intent.resolveActivity(context.packageManager) != null
                if (genericResolve) {
                    context.startActivity(intent)
                    callback(true, "SMS_DELEGATED_GENERIC")
                    true
                } else {
                    callback(false, "SMS_NO_APP")
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SMS fallback 예외")
            callback(false, "SMS_ERROR")
            false
        }
    }
}