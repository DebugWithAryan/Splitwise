package com.aryan.expensesplitwise.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aryan.expensesplitwise.data.local.dao.ExpenseDao
import com.aryan.expensesplitwise.data.local.dao.FriendDao
import com.aryan.expensesplitwise.data.local.dao.MessageDao
import com.aryan.expensesplitwise.data.local.entity.Converters
import com.aryan.expensesplitwise.data.local.entity.ExpenseEntity
import com.aryan.expensesplitwise.data.local.entity.FriendEntity
import com.aryan.expensesplitwise.data.local.entity.MessageEntity

@Database(
    entities = [ExpenseEntity::class, MessageEntity::class, FriendEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun messageDao(): MessageDao
    abstract fun friendDao(): FriendDao
}