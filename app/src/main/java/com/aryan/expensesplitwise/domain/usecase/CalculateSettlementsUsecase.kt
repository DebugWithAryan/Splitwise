package com.aryan.expensesplitwise.domain.usecase

import com.aryan.expensesplitwise.domain.model.Balance
import com.aryan.expensesplitwise.domain.model.Expense
import com.aryan.expensesplitwise.domain.model.Settlement
import javax.inject.Inject
import kotlin.math.abs

class CalculateSettlementsUseCase @Inject constructor() {

    fun calculateBalances(expenses: List<Expense>, friends: List<String>): List<Balance> {
        val balanceMap = mutableMapOf<String, Double>()
        friends.forEach { balanceMap[it] = 0.0 }

        expenses.forEach { expense ->
            val sharePerPerson = expense.amount / expense.splitBetween.size

            // Credit the payer
            balanceMap[expense.paidBy] = (balanceMap[expense.paidBy] ?: 0.0) + expense.amount

            // Debit everyone in the split
            expense.splitBetween.forEach { person ->
                balanceMap[person] = (balanceMap[person] ?: 0.0) - sharePerPerson
            }
        }

        return balanceMap.map { Balance(it.key, it.value) }
            .sortedByDescending { it.amount }
    }

    fun calculateSettlements(balances: List<Balance>): List<Settlement> {
        val balanceMap = balances.associate { it.person to it.amount }.toMutableMap()
        val settlements = mutableListOf<Settlement>()

        while (balanceMap.values.any { abs(it) > 0.01 }) {
            val maxCreditor = balanceMap.maxByOrNull { it.value } ?: break
            val maxDebtor = balanceMap.minByOrNull { it.value } ?: break

            if (abs(maxCreditor.value) < 0.01 || abs(maxDebtor.value) < 0.01) break

            val settlementAmount = minOf(maxCreditor.value, -maxDebtor.value)

            settlements.add(
                Settlement(
                    from = maxDebtor.key,
                    to = maxCreditor.key,
                    amount = settlementAmount
                )
            )

            balanceMap[maxCreditor.key] = maxCreditor.value - settlementAmount
            balanceMap[maxDebtor.key] = maxDebtor.value + settlementAmount
        }

        return settlements
    }
}