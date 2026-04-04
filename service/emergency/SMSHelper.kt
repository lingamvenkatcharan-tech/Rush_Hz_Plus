// service/emergency/SMSHelper.kt
package com.example.rush_hz_plus.service.emergency

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.rush_hz_plus.core.utils.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SMSHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "SMSHelper"
        const val ACTION_SMS_SENT = "com.example.rush_hz_plus.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.example.rush_hz_plus.SMS_DELIVERED"
    }

    private val requestCounter = AtomicInteger(0)
    private val sentCallbacks = mutableMapOf<Int, () -> Unit>()
    private val deliveredCallbacks = mutableMapOf<Int, () -> Unit>()

    private var sentReceiver: BroadcastReceiver? = null
    private var deliveredReceiver: BroadcastReceiver? = null

    // 추가: 기본 SMS 앱 여부 확인
    fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val currentDefault = Telephony.Sms.getDefaultSmsPackage(context)
            currentDefault == context.packageName
        } else {
            true // KitKat 이전은 기본 SMS 개념 없음
        }
    }

    fun sendSms(
        phoneNumber: String,
        message: String,
        onSent: (() -> Unit)? = null,
        onDelivered: (() -> Unit)? = null
    ) {
        if (!hasSmsPermission()) {
            Logger.e(TAG, "SMS 권한 없음. 발송 실패.")
            onSent?.invoke() // 권한 없음 = 즉시 실패 콜백
            return
        }

        // Android 14+ SMS 정책 준수: 기본 SMS 앱 아닐 시 경고 (로그만)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !isDefaultSmsApp()) {
            Logger.w(TAG, "⚠️ Android 14+: 기본 SMS 앱이 아님. 전송 실패 가능성 높음.")
        }

        val formattedNumber = formatPhoneNumber(phoneNumber)
        val smsManager = SmsManager.getDefault()

        try {
            if (message.length <= 160) {
                // 단문 SMS
                val requestCode = requestCounter.getAndIncrement()
                val sentIntent = Intent(ACTION_SMS_SENT).putExtra("requestCode", requestCode)
                val deliveredIntent = Intent(ACTION_SMS_DELIVERED).putExtra("requestCode", requestCode)

                val sentPI = PendingIntent.getBroadcast(
                    context, requestCode, sentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val deliveredPI = if (onDelivered != null) {
                    PendingIntent.getBroadcast(
                        context, requestCode, deliveredIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                } else null

                if (onSent != null) sentCallbacks[requestCode] = onSent
                if (onDelivered != null) deliveredCallbacks[requestCode] = onDelivered!!

                registerReceiversIfNeeded()

                smsManager.sendTextMessage(formattedNumber, null, message, sentPI, deliveredPI)
                Logger.d(TAG, "SMS 발송 요청: $formattedNumber (req=$requestCode, len=${message.length})")
            } else {
                // 장문 SMS → 분할
                val parts = smsManager.divideMessage(message)
                Logger.d(TAG, "긴 SMS 분할: ${parts.size} 부분")

                val sentIntents = parts.mapIndexed { index, _ ->
                    val req = requestCounter.getAndIncrement()
                    // 마지막 파트에서만 onSent 호출
                    sentCallbacks[req] = {
                        if (index == parts.size - 1) onSent?.invoke()
                    }
                    PendingIntent.getBroadcast(
                        context, req,
                        Intent(ACTION_SMS_SENT).putExtra("requestCode", req),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }

                val deliveryIntents = if (onDelivered != null) {
                    parts.mapIndexed { index, _ ->
                        val req = requestCounter.getAndIncrement()
                        // 마지막 파트에서만 onDelivered 호출
                        deliveredCallbacks[req] = {
                            if (index == parts.size - 1) onDelivered?.invoke()
                        }
                        PendingIntent.getBroadcast(
                            context, req,
                            Intent(ACTION_SMS_DELIVERED).putExtra("requestCode", req),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    }
                } else null

                registerReceiversIfNeeded()

                smsManager.sendMultipartTextMessage(
                    formattedNumber, null, parts,
                    sentIntents as ArrayList<PendingIntent>?, deliveryIntents as ArrayList<PendingIntent>?
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, "SMS 발송 중 예외 발생", e)
            onSent?.invoke() // 예외 = 실패로 간주
        }
    }

    private fun registerReceiversIfNeeded() {
        if (sentReceiver != null) return

        try {
            sentReceiver = SentReceiver()
            context.registerReceiver(
                sentReceiver!!,
                IntentFilter(ACTION_SMS_SENT),
                Context.RECEIVER_NOT_EXPORTED
            )

            deliveredReceiver = DeliveredReceiver()
            context.registerReceiver(
                deliveredReceiver!!,
                IntentFilter(ACTION_SMS_DELIVERED),
                Context.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Receiver 등록 실패", e)
        }
    }

    // 새로 추가: 외부에서 명시적 해제 가능
    fun unregisterBroadcastReceivers() {
        try {
            sentReceiver?.let { context.unregisterReceiver(it) }
            deliveredReceiver?.let { context.unregisterReceiver(it) }
        } catch (e: IllegalArgumentException) {
            // 이미 해제된 경우 무시
        } finally {
            sentReceiver = null
            deliveredReceiver = null
            sentCallbacks.clear()
            deliveredCallbacks.clear()
        }
    }

    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun formatPhoneNumber(phone: String): String {
        val clean = phone.replace(Regex("[^0-9+]"), "")
        if (clean.isEmpty()) throw IllegalArgumentException("Empty phone number")

        return when {
            clean.startsWith("+") -> clean
            clean.startsWith("82") && clean.length >= 11 -> "+$clean"
            clean.startsWith("0") && (clean.length == 10 || clean.length == 11) -> "+82${clean.substring(1)}"
            clean.length >= 9 -> "+82$clean"
            else -> throw IllegalArgumentException("Invalid phone number: $phone")
        }
    }

    private inner class SentReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val requestCode = intent.getIntExtra("requestCode", -1)
            if (requestCode == -1) return

            val callback = sentCallbacks[requestCode]
            val reason = when (resultCode) {
                android.app.Activity.RESULT_OK -> {
                    Logger.d(TAG, "✅ SMS 전송 요청 성공 (단말기 내부 큐 등록 완료) (req=$requestCode)")
                    null
                }
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "일반 오류 (carrier/network 거부 가능성 높음)"
                SmsManager.RESULT_ERROR_RADIO_OFF -> "비행기 모드 또는 무선 꺼짐"
                SmsManager.RESULT_ERROR_NULL_PDU -> "PDU null"
                SmsManager.RESULT_ERROR_NO_SERVICE -> "이동통신사 연결 없음"
                else -> "알 수 없는 오류 ($resultCode)"
            }

            if (reason != null) {
                Logger.e(TAG, "❌ SMS 전송 요청 실패: $reason (req=$requestCode)")
            }

            callback?.invoke() // 성공/실패 모두 콜백 호출
            cleanupCallback(requestCode)
        }
    }

    private inner class DeliveredReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val requestCode = intent.getIntExtra("requestCode", -1)
            if (requestCode == -1) return

            val callback = deliveredCallbacks[requestCode]
            when (resultCode) {
                android.app.Activity.RESULT_OK -> {
                    Logger.d(TAG, "📦 SMS 수신자 단말기까지 도착 확인됨 (req=$requestCode)")
                }
                else -> {
                    Logger.w(TAG, "⚠️ SMS 수신 미확인 (req=$requestCode, result=$resultCode)")
                }
            }
            callback?.invoke()
            cleanupCallback(requestCode)
        }
    }

    private fun cleanupCallback(requestCode: Int) {
        sentCallbacks.remove(requestCode)
        deliveredCallbacks.remove(requestCode)
    }
}