package com.aryan.expensesplitwise.data.local.entity


import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "expenses")
@TypeConverters(Converters::class)
data class ExpenseEntity(
    @PrimaryKey
    val id: String,
    val description: String,
    val amount: Double,
    val paidBy: String,
    val splitBetween: List<String>,
    val timestamp: Long,
    val detectedFromMessage: Boolean
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val text: String,
    val processed: Boolean,
    val timestamp: Long
)

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val addedAt: Long
)

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType)
    }
}