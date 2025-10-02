package com.aryan.expensesplitwise.domain.usecase

import com.aryan.expensesplitwise.domain.model.Expense
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class ParseMessageUseCase @Inject constructor() {

    data class ParseResult(
        val expense: Expense?,
        val success: Boolean
    )

    fun execute(text: String, friends: List<String>, messageTimestamp: Long = System.currentTimeMillis()): ParseResult {
        val amount = extractAmount(text) ?: return ParseResult(null, false)
        val paidBy = detectPayer(text, friends)
        val description = extractDescription(text)
        val splitBetween = detectSplitBetween(text, friends, paidBy)

        // Extract payment date from message, fallback to message timestamp
        val paymentTimestamp = extractPaymentDate(text, messageTimestamp)

        val expense = Expense(
            id = UUID.randomUUID().toString(),
            description = description,
            amount = amount,
            paidBy = paidBy,
            splitBetween = splitBetween,
            timestamp = paymentTimestamp,
            detectedFromMessage = true
        )

        return ParseResult(expense, true)
    }

    private fun extractPaymentDate(text: String, fallbackTimestamp: Long): Long {
        try {
            val lowerText = text.lowercase()

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

        val amountPattern = """(?:paid|debited|sent|transferred)\s+(?:Rs\.?|₹|INR)?\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE)
        amountPattern.find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        return null
    }

    private fun extractDescription(text: String): String {
        val lowerText = text.lowercase()

        val toPattern = """(?:to|paid to|sent to)\s+([A-Za-z\s]+?)(?:\s+(?:Rs|INR|₹|for|on|via|using)|$)""".toRegex(RegexOption.IGNORE_CASE)
        toPattern.find(text)?.let {
            val recipient = it.groupValues[1].trim()
            if (recipient.length > 2 && !recipient.contains("account", ignoreCase = true)) {
                return recipient
            }
        }

        return when {
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
            lowerText.contains("upi") -> "UPI Payment"
            else -> "Payment"
        }
    }

    private fun detectPayer(text: String, friends: List<String>): String {
        val lowerText = text.lowercase()

        if (lowerText.contains("debited") ||
            lowerText.contains("you sent") ||
            lowerText.contains("you paid") ||
            lowerText.contains("your payment") ||
            lowerText.contains("your account")) {
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

    private fun detectSplitBetween(text: String, friends: List<String>, paidBy: String): List<String> {
        val lowerText = text.lowercase()
        val splitBetween = mutableSetOf<String>()

        if (lowerText.contains("all of us") ||
            lowerText.contains("everyone") ||
            lowerText.contains("split between all")) {
            return friends
        }

        friends.forEach { friend ->
            if (lowerText.contains(friend.lowercase())) {
                splitBetween.add(friend)
            }
        }

        if (lowerText.contains("me and") || lowerText.contains("for me and")) {
            splitBetween.add(paidBy)
        }

        if (splitBetween.isEmpty()) {
            splitBetween.add(paidBy)
        }

        return splitBetween.toList()
    }
}