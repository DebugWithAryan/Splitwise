package com.aryan.expensesplitwise

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.aryan.expensesplitwise.domain.model.Message
import com.aryan.expensesplitwise.domain.repository.ExpenseRepository
import com.aryan.expensesplitwise.domain.usecase.ParseMessageUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ExpenseRepository

    @Inject
    lateinit var parseMessageUseCase: ParseMessageUseCase

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            messages?.forEach { smsMessage ->
                val sender = smsMessage.displayOriginatingAddress
                val messageBody = smsMessage.messageBody
                val timestamp = smsMessage.timestampMillis

                Log.d("SmsReceiver", "SMS from $sender: $messageBody")

                if (isPaymentSms(sender, messageBody)) {
                    scope.launch {
                        try {
                            // Add message to database
                            val message = Message(
                                id = UUID.randomUUID().toString(),
                                text = messageBody,
                                processed = false,
                                timestamp = timestamp
                            )
                            repository.addMessage(message)

                            // Get friends list and parse the message
                            val friends = repository.getAllFriends().first()
                            val friendNames = friends.map { it.name }

                            val parseResult = parseMessageUseCase.execute(
                                messageBody,
                                friendNames,
                                timestamp
                            )

                            // If parsing successful, add expense
                            if (parseResult.success && parseResult.expense != null) {
                                repository.addExpense(parseResult.expense)
                                repository.updateMessageProcessed(message.id, true)
                                Log.d("SmsReceiver", "Expense added: ${parseResult.expense.description}")
                            } else {
                                Log.d("SmsReceiver", "Failed to parse message")
                            }
                        } catch (e: Exception) {
                            Log.e("SmsReceiver", "Error processing SMS", e)
                        }
                    }
                }
            }
        }
    }

    private fun isPaymentSms(sender: String, message: String): Boolean {
        val paymentKeywords = listOf(
            "paid", "debited", "spent", "sent", "transferred",
            "upi", "transaction", "payment", "credited", "received"
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