package com.aryan.expensesplitwise.domain.usecase

import com.aryan.expensesplitwise.domain.model.Expense
import java.util.UUID
import javax.inject.Inject

class ParseMessageUseCase @Inject constructor() {

    data class ParseResult(
        val expense: Expense?,
        val success: Boolean
    )

    fun execute(text: String, friends: List<String>): ParseResult {
        // Extract amount - supports Rs, ₹, INR formats
        val amount = extractAmount(text) ?: return ParseResult(null, false)

        // Detect who paid
        val paidBy = detectPayer(text, friends)

        // Detect recipient/merchant for description
        val description = extractDescription(text)

        // For payment SMS, split between payer only unless explicitly mentioned
        val splitBetween = detectSplitBetween(text, friends, paidBy)

        val expense = Expense(
            id = UUID.randomUUID().toString(),
            description = description,
            amount = amount,
            paidBy = paidBy,
            splitBetween = splitBetween,
            timestamp = System.currentTimeMillis(),
            detectedFromMessage = true
        )

        return ParseResult(expense, true)
    }

    private fun extractAmount(text: String): Double? {
        // Pattern 1: Rs 500, Rs.500, Rs500
        val rsPattern = """[Rr][Ss]\.?\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex()
        rsPattern.find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        // Pattern 2: ₹500, ₹ 500
        val rupeeSymbolPattern = """₹\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex()
        rupeeSymbolPattern.find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        // Pattern 3: INR 500, INR500
        val inrPattern = """[Ii][Nn][Rr]\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex()
        inrPattern.find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        // Pattern 4: $500 (for manual messages)
        val dollarPattern = """\$\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex()
        dollarPattern.find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        // Pattern 5: Just amount with "paid", "debited", "sent"
        val amountPattern = """(?:paid|debited|sent|transferred)\s+(?:Rs\.?|₹|INR)?\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE)
        amountPattern.find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        return null
    }

    private fun extractDescription(text: String): String {
        val lowerText = text.lowercase()

        // Extract merchant/recipient name from UPI patterns
        // Pattern: "to <name>" or "paid to <name>" or "sent to <name>"
        val toPattern = """(?:to|paid to|sent to)\s+([A-Za-z\s]+?)(?:\s+(?:Rs|INR|₹|for|on|via|using)|$)""".toRegex(RegexOption.IGNORE_CASE)
        toPattern.find(text)?.let {
            val recipient = it.groupValues[1].trim()
            if (recipient.length > 2 && !recipient.contains("account", ignoreCase = true)) {
                return recipient
            }
        }

        // Check for common keywords
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

        // For SMS messages from payment apps, assume "Me" paid
        if (lowerText.contains("debited") ||
            lowerText.contains("you sent") ||
            lowerText.contains("you paid") ||
            lowerText.contains("your payment") ||
            lowerText.contains("your account")) {
            return "Me"
        }

        // Check for "I paid" or "I spent"
        if (lowerText.contains("i paid") || lowerText.contains("i spent")) {
            return "Me"
        }

        // Check for explicit names
        friends.forEach { friend ->
            if (lowerText.contains("${friend.lowercase()} paid") ||
                lowerText.contains("${friend.lowercase()} spent")) {
                return friend
            }
        }

        // Default to "Me" for payment SMS
        return "Me"
    }

    private fun detectSplitBetween(text: String, friends: List<String>, paidBy: String): List<String> {
        val lowerText = text.lowercase()
        val splitBetween = mutableSetOf<String>()

        // Check for "all" or "everyone"
        if (lowerText.contains("all of us") ||
            lowerText.contains("everyone") ||
            lowerText.contains("split between all")) {
            return friends
        }

        // Check for explicit names
        friends.forEach { friend ->
            if (lowerText.contains(friend.lowercase())) {
                splitBetween.add(friend)
            }
        }

        // Check for "me and [name]" pattern
        if (lowerText.contains("me and") || lowerText.contains("for me and")) {
            splitBetween.add(paidBy)
        }

        // If empty, default to payer only (for payment SMS, it's usually personal)
        if (splitBetween.isEmpty()) {
            splitBetween.add(paidBy)
        }

        return splitBetween.toList()
    }
}