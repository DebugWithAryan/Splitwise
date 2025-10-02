package com.aryan.expensesplitwise.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aryan.expensesplitwise.SmsReader
import com.aryan.expensesplitwise.domain.model.Balance
import com.aryan.expensesplitwise.domain.model.Expense
import com.aryan.expensesplitwise.domain.model.Friend
import com.aryan.expensesplitwise.domain.model.Message
import com.aryan.expensesplitwise.domain.model.Settlement
import com.aryan.expensesplitwise.domain.repository.ExpenseRepository
import com.aryan.expensesplitwise.domain.usecase.CalculateSettlementsUseCase
import com.aryan.expensesplitwise.domain.usecase.ParseMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val parseMessageUseCase: ParseMessageUseCase,
    private val calculateSettlementsUseCase: CalculateSettlementsUseCase,
    private val smsReader: SmsReader
) : ViewModel() {

    val expenses: StateFlow<List<Expense>> = repository.getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages: StateFlow<List<Message>> = repository.getAllMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val friends: StateFlow<List<Friend>> = repository.getAllFriends()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val balances: StateFlow<List<Balance>> = combine(expenses, friends) { expenseList, friendList ->
        calculateSettlementsUseCase.calculateBalances(
            expenseList,
            friendList.map { it.name }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settlements: StateFlow<List<Settlement>> = balances.map { balanceList ->
        calculateSettlementsUseCase.calculateSettlements(balanceList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isScanningSms = MutableStateFlow(false)
    val isScanningSms: StateFlow<Boolean> = _isScanningSms.asStateFlow()

    fun addMessage(text: String) {
        viewModelScope.launch {
            val message = Message(
                id = UUID.randomUUID().toString(),
                text = text,
                processed = false,
                timestamp = System.currentTimeMillis()
            )
            repository.addMessage(message)

            // Try to parse the message
            val friendNames = friends.value.map { it.name }
            val parseResult = parseMessageUseCase.execute(text, friendNames)

            if (parseResult.success && parseResult.expense != null) {
                repository.addExpense(parseResult.expense)
                repository.updateMessageProcessed(message.id, true)
            }
        }
    }

    fun scanHistoricalSms(daysBack: Int = 30) {
        viewModelScope.launch {
            _isScanningSms.value = true
            try {
                val smsMessages = smsReader.readRecentPaymentSms(daysBack)
                val friendNames = friends.value.map { it.name }

                smsMessages.forEach { sms ->
                    val message = Message(
                        id = UUID.randomUUID().toString(),
                        text = sms.body,
                        processed = false,
                        timestamp = sms.date
                    )
                    repository.addMessage(message)

                    // Try to parse
                    val parseResult = parseMessageUseCase.execute(sms.body, friendNames)
                    if (parseResult.success && parseResult.expense != null) {
                        repository.addExpense(parseResult.expense)
                        repository.updateMessageProcessed(message.id, true)
                    }
                }
            } finally {
                _isScanningSms.value = false
            }
        }
    }

    fun addExpense(description: String, amount: Double, paidBy: String, splitBetween: List<String>) {
        viewModelScope.launch {
            val expense = Expense(
                id = UUID.randomUUID().toString(),
                description = description,
                amount = amount,
                paidBy = paidBy,
                splitBetween = splitBetween,
                timestamp = System.currentTimeMillis(),
                detectedFromMessage = false
            )
            repository.addExpense(expense)
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    fun addFriend(name: String) {
        viewModelScope.launch {
            repository.addFriend(name)
        }
    }

    fun deleteFriend(friend: Friend) {
        viewModelScope.launch {
            repository.deleteFriend(friend)
        }
    }
}