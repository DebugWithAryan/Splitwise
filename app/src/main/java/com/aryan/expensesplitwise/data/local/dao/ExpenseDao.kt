package com.aryan.expensesplitwise.data.local.dao

import androidx.room.*
import com.aryan.expensesplitwise.data.local.entity.ExpenseEntity
import com.aryan.expensesplitwise.data.local.entity.FriendEntity
import com.aryan.expensesplitwise.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET processed = :processed WHERE id = :messageId")
    suspend fun updateMessageProcessed(messageId: String, processed: Boolean)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)
}

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends ORDER BY name ASC")
    fun getAllFriends(): Flow<List<FriendEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: FriendEntity)

    @Delete
    suspend fun deleteFriend(friend: FriendEntity)

    @Query("SELECT COUNT(*) FROM friends WHERE name = :name")
    suspend fun friendExists(name: String): Int
}