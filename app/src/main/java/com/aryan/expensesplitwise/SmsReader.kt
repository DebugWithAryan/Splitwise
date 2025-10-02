package com.aryan.expensesplitwise

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class SmsMessage(
        val id: Long,
        val address: String,
        val body: String,
        val date: Long
    )

    fun readRecentPaymentSms(daysBack: Int = 30): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val cutoffTime = System.currentTimeMillis() - (daysBack * 24 * 60 * 60 * 1000L)

        try {
            val cursor: Cursor? = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                ),
                "${Telephony.Sms.DATE} > ?",
                arrayOf(cutoffTime.toString()),
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val address = it.getString(addressIndex) ?: ""
                    val body = it.getString(bodyIndex) ?: ""
                    val date = it.getLong(dateIndex)

                    if (isPaymentSms(address, body)) {
                        messages.add(SmsMessage(id, address, body, date))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReader", "Error reading SMS", e)
        }

        return messages
    }

    private fun isPaymentSms(sender: String, message: String): Boolean {
        val paymentKeywords = listOf(
            "paid", "debited", "spent", "sent", "transferred",
            "upi", "transaction", "payment"
        )

        val paymentSenders = listOf(
            "PHONEPE", "PAYTM", "GPAY", "GOOGLEPAY", "AMAZONPAY",
            "BHIM", "SBI", "HDFC", "ICICI", "AXIS", "KOTAK",
            "MOBIKWIK", "FREECHARGE", "WHATSAPP", "BHARATPE"
        )

        val lowerMessage = message.lowercase()
        val upperSender = sender.uppercase()

        val isPaymentSender = paymentSenders.any { upperSender.contains(it) }
        val hasPaymentKeyword = paymentKeywords.any { lowerMessage.contains(it) }
        val hasAmount = lowerMessage.contains("rs") ||
                lowerMessage.contains("inr") ||
                lowerMessage.contains("₹") ||
                Regex("""\d+(\.\d{1,2})?""").find(message) != null

        return isPaymentSender && hasPaymentKeyword && hasAmount
    }
}