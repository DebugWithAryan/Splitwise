package com.aryan.expensesplitwise.domain.model


data class Expense(
    val id: String,
    val description: String,
    val amount: Double,
    val paidBy: String,
    val splitBetween: List<String>,
    val timestamp: Long,
    val detectedFromMessage: Boolean
)

data class Message(
    val id: String,
    val text: String,
    val processed: Boolean,
    val timestamp: Long
)

data class Friend(
    val id: String,
    val name: String,
    val addedAt: Long
)

data class Settlement(
    val from: String,
    val to: String,
    val amount: Double
)

data class Balance(
    val person: String,
    val amount: Double
)

