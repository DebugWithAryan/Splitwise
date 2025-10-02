package com.aryan.expensesplitwise

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.aryan.expensesplitwise.domain.repository.ExpenseRepository
import com.aryan.expensesplitwise.domain.model.Message
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ExpenseRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            messages?.forEach { smsMessage ->
                val sender = smsMessage.displayOriginatingAddress
                val messageBody = smsMessage.messageBody

                Log.d("SmsReceiver", "SMS from $sender: $messageBody")

                // Check if it's a payment SMS
                if (isPaymentSms(sender, messageBody)) {
                    scope.launch {
                        val message = Message(
                            id = UUID.randomUUID().toString(),
                            text = messageBody,
                            processed = false,
                            timestamp = System.currentTimeMillis()
                        )
                        repository.addMessage(message)
                    }
                }
            }
        }
    }

    private fun isPaymentSms(sender: String, message: String): Boolean {
        val paymentKeywords = listOf(
            "paid", "debited", "spent", "sent", "transferred",
            "upi", "transaction", "payment"
        )

        val paymentSenders = listOf(
            "PHONEPE", "PAYTM", "GPAY", "GOOGLEPAY", "AMAZONPAY",
            "BHIM", "SBI", "HDFC", "ICICI", "AXIS", "KOTAK",
            "MOBIKWIK", "FREECHARGE", "WHATSAPP"
        )

        val lowerMessage = message.lowercase()
        val upperSender = sender.uppercase()

        // Check if sender is a known payment app/bank
        val isPaymentSender = paymentSenders.any { upperSender.contains(it) }

        // Check if message contains payment keywords
        val hasPaymentKeyword = paymentKeywords.any { lowerMessage.contains(it) }

        // Check if message contains amount pattern (Rs., INR, ₹, or just numbers)
        val hasAmount = lowerMessage.contains("rs") ||
                lowerMessage.contains("inr") ||
                lowerMessage.contains("₹") ||
                Regex("""\d+(\.\d{1,2})?""").find(message) != null

        return isPaymentSender && hasPaymentKeyword && hasAmount
    }
}