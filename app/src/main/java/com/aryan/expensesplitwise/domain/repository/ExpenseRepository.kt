package com.aryan.expensesplitwise.domain.repository

import com.aryan.expensesplitwise.data.local.dao.ExpenseDao
import com.aryan.expensesplitwise.data.local.dao.FriendDao
import com.aryan.expensesplitwise.data.local.dao.MessageDao
import com.aryan.expensesplitwise.data.local.entity.ExpenseEntity
import com.aryan.expensesplitwise.data.local.entity.FriendEntity
import com.aryan.expensesplitwise.data.local.entity.MessageEntity
import com.aryan.expensesplitwise.domain.model.Expense
import com.aryan.expensesplitwise.domain.model.Friend
import com.aryan.expensesplitwise.domain.model.Message
import com.aryan.expensesplitwise.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val messageDao: MessageDao,
    private val friendDao: FriendDao
) {

    fun getAllExpenses(): Flow<List<Expense>> =
        expenseDao.getAllExpenses().map { entities ->
            entities.map { it.toDomain() }
        }

    fun getAllMessages(): Flow<List<Message>> =
        messageDao.getAllMessages().map { entities ->
            entities.map { it.toDomain() }
        }

    fun getAllFriends(): Flow<List<Friend>> =
        friendDao.getAllFriends().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun addExpense(expense: Expense) {
        expenseDao.insertExpense(expense.toEntity())
    }

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.deleteExpense(expense.toEntity())
    }

    suspend fun addMessage(message: Message) {
        messageDao.insertMessage(message.toEntity())
    }

    suspend fun updateMessageProcessed(messageId: String, processed: Boolean) {
        messageDao.updateMessageProcessed(messageId, processed)
    }

    suspend fun addFriend(name: String) {
        if (friendDao.friendExists(name) == 0) {
            friendDao.insertFriend(
                FriendEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteFriend(friend: Friend) {
        friendDao.deleteFriend(friend.toEntity())
    }

    private fun ExpenseEntity.toDomain() = Expense(
        id = id,
        description = description,
        amount = amount,
        paidBy = paidBy,
        splitBetween = splitBetween,
        timestamp = timestamp,
        detectedFromMessage = detectedFromMessage,
        transactionType = try {
            TransactionType.valueOf(transactionType)
        } catch (e: Exception) {
            TransactionType.OUTGOING
        }
    )

    private fun Expense.toEntity() = ExpenseEntity(
        id = id,
        description = description,
        amount = amount,
        paidBy = paidBy,
        splitBetween = splitBetween,
        timestamp = timestamp,
        detectedFromMessage = detectedFromMessage,
        transactionType = transactionType.name
    )

    private fun MessageEntity.toDomain() = Message(id, text, processed, timestamp)

    private fun Message.toEntity() = MessageEntity(id, text, processed, timestamp)

    private fun FriendEntity.toDomain() = Friend(id, name, addedAt)

    private fun Friend.toEntity() = FriendEntity(id, name, addedAt)
}