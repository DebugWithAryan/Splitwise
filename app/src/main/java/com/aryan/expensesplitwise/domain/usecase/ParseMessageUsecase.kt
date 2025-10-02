package com.aryan.expensesplitwise.domain.usecase

import com.aryan.expensesplitwise.domain.model.Expense
import com.aryan.expensesplitwise.domain.model.TransactionType
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

class ParseMessageUseCase @Inject constructor() {

    data class ParseResult(
        val expense: Expense?,
        val success: Boolean
    )

    fun execute(text: String, friends: List<String>, messageTimestamp: Long = System.currentTimeMillis()): ParseResult {
        val amount = extractAmount(text) ?: return ParseResult(null, false)

        // Detect transaction type (incoming vs outgoing)
        val transactionType = detectTransactionType(text)

        val paidBy = detectPayer(text, friends, transactionType)
        val description = extractDescription(text, transactionType)
        val splitBetween = detectSplitBetween(text, friends, paidBy, transactionType)

        val paymentTimestamp = extractPaymentDate(text, messageTimestamp)

        val expense = Expense(
            id = UUID.randomUUID().toString(),
            description = description,
            amount = amount,
            paidBy = paidBy,
            splitBetween = splitBetween,
            timestamp = paymentTimestamp,
            detectedFromMessage = true,
            transactionType = transactionType
        )

        return ParseResult(expense, true)
    }

    private fun detectTransactionType(text: String): TransactionType {
        val lowerText = text.lowercase()

        // Check for incoming/credited keywords with priority
        val incomingKeywords = listOf(
            "credited to", "credited in", "credited",
            "received from", "received in", "received",
            "refund", "refunded", "cashback",
            "you received", "got money", "incoming",
            "deposited", "deposit to"
        )

        // Check for outgoing/debited keywords
        val outgoingKeywords = listOf(
            "debited from", "debited",
            "paid to", "paid for", "paid",
            "sent to", "sent",
            "transferred to", "transferred",
            "you paid", "you sent",
            "purchase", "withdrawn", "spent"
        )

        // Count matches for each type
        val incomingMatches = incomingKeywords.count { lowerText.contains(it) }
        val outgoingMatches = outgoingKeywords.count { lowerText.contains(it) }

        // Prioritize incoming if it has more matches or equal matches
        return when {
            incomingMatches > outgoingMatches -> TransactionType.INCOMING
            incomingMatches > 0 && outgoingMatches == 0 -> TransactionType.INCOMING
            else -> TransactionType.OUTGOING
        }
    }

    private fun extractPaymentDate(text: String, fallbackTimestamp: Long): Long {
        try {
            // Pattern 1: "on 25 Dec 2024" or "on 25-Dec-2024"
            val datePattern1 = """on\s+(\d{1,2})[- ]([A-Za-z]{3})[- ](\d{4})""".toRegex(RegexOption.IGNORE_CASE)
            datePattern1.find(text)?.let { match ->
                val day = match.groupValues[1].toInt()
                val month = match.groupValues[2]
                val year = match.groupValues[3].toInt()
                return parseDate(day, month, year)
            }

            // Pattern 2: "on 25/12/2024" or "on 25-12-2024"
            val datePattern2 = """on\s+(\d{1,2})[/-](\d{1,2})[/-](\d{4})""".toRegex()
            datePattern2.find(text)?.let { match ->
                val day = match.groupValues[1].toInt()
                val month = match.groupValues[2].toInt()
                val year = match.groupValues[3].toInt()
                return parseDate(day, month, year)
            }

            // Pattern 3: "25 Dec" or "25-Dec" (assume current year)
            val datePattern3 = """(\d{1,2})[- ]([A-Za-z]{3})""".toRegex(RegexOption.IGNORE_CASE)
            datePattern3.find(text)?.let { match ->
                val day = match.groupValues[1].toInt()
                val month = match.groupValues[2]
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                return parseDate(day, month, currentYear)
            }

            // Pattern 4: "at 14:30" or "at 2:30 PM" - use fallback date with extracted time
            val timePattern = """at\s+(\d{1,2}):(\d{2})\s*(AM|PM)?""".toRegex(RegexOption.IGNORE_CASE)
            timePattern.find(text)?.let { match ->
                val hour = match.groupValues[1].toInt()
                val minute = match.groupValues[2].toInt()
                val ampm = match.groupValues[3].uppercase()

                val calendar = Calendar.getInstance().apply {
                    timeInMillis = fallbackTimestamp
                    set(Calendar.MINUTE, minute)

                    if (ampm.isNotEmpty()) {
                        var adjustedHour = hour
                        if (ampm == "PM" && hour != 12) adjustedHour += 12
                        if (ampm == "AM" && hour == 12) adjustedHour = 0
                        set(Calendar.HOUR_OF_DAY, adjustedHour)
                    } else {
                        set(Calendar.HOUR_OF_DAY, hour)
                    }
                }
                return calendar.timeInMillis
            }

        } catch (e: Exception) {
            // Fallback to message timestamp on any parsing error
        }

        return fallbackTimestamp
    }

    private fun parseDate(day: Int, month: Any, year: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.YEAR, year)

        when (month) {
            is Int -> calendar.set(Calendar.MONTH, month - 1)
            is String -> {
                val monthMap = mapOf(
                    "jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3,
                    "may" to 4, "jun" to 5, "jul" to 6, "aug" to 7,
                    "sep" to 8, "oct" to 9, "nov" to 10, "dec" to 11
                )
                calendar.set(Calendar.MONTH, monthMap[month.lowercase()] ?: 0)
            }
        }

        return calendar.timeInMillis
    }

    private fun extractAmount(text: String): Double? {
        val rsPattern = """[Rr][Ss]\.?\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex()
        rsPattern.find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        val rupeeSymbolPattern = """₹\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex()
        rupeeSymbolPattern.find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        val inrPattern = """[Ii][Nn][Rr]\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex()
        inrPattern.find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        val dollarPattern = """\$\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex()
        dollarPattern.find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        val amountPattern = """(?:paid|debited|sent|transferred|credited|received)\s+(?:Rs\.?|₹|INR)?\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE)
        amountPattern.find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        return null
    }

    private fun extractDescription(text: String, transactionType: TransactionType): String {
        val lowerText = text.lowercase()

        // Extract merchant/recipient name from UPI patterns
        val toFromPattern = if (transactionType == TransactionType.INCOMING) {
            """(?:from|received from)\s+([A-Za-z\s]+?)(?:\s+(?:Rs|INR|₹|for|on|via|using)|$)""".toRegex(RegexOption.IGNORE_CASE)
        } else {
            """(?:to|paid to|sent to)\s+([A-Za-z\s]+?)(?:\s+(?:Rs|INR|₹|for|on|via|using)|$)""".toRegex(RegexOption.IGNORE_CASE)
        }

        toFromPattern.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name.length > 2 && !name.contains("account", ignoreCase = true)) {
                return if (transactionType == TransactionType.INCOMING) {
                    "Received from $name"
                } else {
                    "Paid to $name"
                }
            }
        }

        // Category-based description with proper prefix
        val prefix = if (transactionType == TransactionType.INCOMING) "Received: " else ""

        return prefix + when {
            lowerText.contains("movie") || lowerText.contains("ticket") -> "Movie Tickets"
            lowerText.contains("dinner") || lowerText.contains("restaurant") -> "Dinner"
            lowerText.contains("lunch") -> "Lunch"
            lowerText.contains("breakfast") -> "Breakfast"
            lowerText.contains("coffee") || lowerText.contains("cafe") -> "Coffee"
            lowerText.contains("grocery") || lowerText.contains("groceries") -> "Groceries"
            lowerText.contains("uber") || lowerText.contains("ola") ||
                    lowerText.contains("taxi") || lowerText.contains("cab") -> "Transportation"
            lowerText.contains("swiggy") || lowerText.contains("zomato") -> "Food Delivery"
            lowerText.contains("amazon") -> "Shopping"
            lowerText.contains("flipkart") -> "Shopping"
            lowerText.contains("gas") || lowerText.contains("petrol") || lowerText.contains("fuel") -> "Fuel"
            lowerText.contains("rent") -> "Rent"
            lowerText.contains("electricity") || lowerText.contains("water") || lowerText.contains("utilities") -> "Utilities"
            lowerText.contains("internet") || lowerText.contains("wifi") || lowerText.contains("broadband") -> "Internet"
            lowerText.contains("pizza") -> "Pizza"
            lowerText.contains("drinks") || lowerText.contains("bar") -> "Drinks"
            lowerText.contains("hotel") -> "Hotel"
            lowerText.contains("flight") || lowerText.contains("airline") -> "Flight"
            lowerText.contains("medicine") || lowerText.contains("pharmacy") -> "Medicine"
            lowerText.contains("recharge") -> "Recharge"
            lowerText.contains("cashback") -> "Cashback"
            lowerText.contains("refund") -> "Refund"
            lowerText.contains("salary") -> "Salary"
            lowerText.contains("upi") -> if (transactionType == TransactionType.INCOMING) "UPI Received" else "UPI Payment"
            else -> if (transactionType == TransactionType.INCOMING) "Money Received" else "Payment"
        }
    }

    private fun detectPayer(text: String, friends: List<String>, transactionType: TransactionType): String {
        val lowerText = text.lowercase()

        // For INCOMING transactions, someone else paid YOU
        if (transactionType == TransactionType.INCOMING) {
            // Check for explicit sender names
            friends.forEach { friend ->
                if (lowerText.contains("from ${friend.lowercase()}") ||
                    lowerText.contains("${friend.lowercase()} sent") ||
                    lowerText.contains("${friend.lowercase()} paid")) {
                    return friend
                }
            }
            // Default: unknown sender for incoming (they paid you)
            return "Unknown"
        }

        // For OUTGOING transactions, YOU or a friend paid
        if (lowerText.contains("debited") ||
            lowerText.contains("you sent") ||
            lowerText.contains("you paid") ||
            lowerText.contains("your payment") ||
            lowerText.contains("your account") ||
            lowerText.contains("withdrawn from")) {
            return "Me"
        }

        if (lowerText.contains("i paid") || lowerText.contains("i spent")) {
            return "Me"
        }

        friends.forEach { friend ->
            if (lowerText.contains("${friend.lowercase()} paid") ||
                lowerText.contains("${friend.lowercase()} spent")) {
                return friend
            }
        }

        return "Me"
    }

    private fun detectSplitBetween(text: String, friends: List<String>, paidBy: String, transactionType: TransactionType): List<String> {
        val lowerText = text.lowercase()
        val splitBetween = mutableSetOf<String>()

        // For INCOMING money, only YOU benefit (receive the money)
        if (transactionType == TransactionType.INCOMING) {
            splitBetween.add("Me")
            return splitBetween.toList()
        }

        // For OUTGOING payments - detect who to split with
        if (lowerText.contains("all of us") ||
            lowerText.contains("everyone") ||
            lowerText.contains("split between all") ||
            lowerText.contains("split equally")) {
            splitBetween.add("Me")
            splitBetween.addAll(friends)
            return splitBetween.toList()
        }

        // Check for specific friend mentions
        friends.forEach { friend ->
            if (lowerText.contains(friend.lowercase())) {
                splitBetween.add(friend)
            }
        }

        // Check if payer should be included
        if (lowerText.contains("me and") ||
            lowerText.contains("for me and") ||
            lowerText.contains("myself and")) {
            splitBetween.add(paidBy)
        }

        // If no one detected, default to payer only
        if (splitBetween.isEmpty()) {
            splitBetween.add(paidBy)
        }

        return splitBetween.toList()
    }
}