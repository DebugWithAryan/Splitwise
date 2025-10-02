package com.aryan.expensesplitwise.di

import android.content.Context
import androidx.room.Room
import com.aryan.expensesplitwise.SmsReader
import com.aryan.expensesplitwise.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "expense_splitter_db"
        ).build()
    }

    @Provides
    fun provideExpenseDao(database: AppDatabase) = database.expenseDao()

    @Provides
    fun provideMessageDao(database: AppDatabase) = database.messageDao()

    @Provides
    fun provideFriendDao(database: AppDatabase) = database.friendDao()

    @Provides
    @Singleton
    fun provideSmsReader(@ApplicationContext context: Context): SmsReader {
        return SmsReader(context)
    }
}