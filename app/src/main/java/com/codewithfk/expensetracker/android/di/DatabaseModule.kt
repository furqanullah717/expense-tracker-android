package com.codewithfk.expensetracker.android.di

import android.content.Context
import com.codewithfk.expensetracker.android.data.ExpenseDatabase
import com.codewithfk.expensetracker.android.data.dao.AiAnalysisDao
import com.codewithfk.expensetracker.android.data.dao.ChatDao
import com.codewithfk.expensetracker.android.data.dao.ExpenseDao
import com.codewithfk.expensetracker.android.data.repository.ChatRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ExpenseDatabase {
        return ExpenseDatabase.getInstance(context)
    }

    @Provides
    fun provideExpenseDao(database: ExpenseDatabase): ExpenseDao {
        return database.expenseDao()
    }

    @Provides
    fun provideAiAnalysisDao(database: ExpenseDatabase): AiAnalysisDao {
        return database.aiAnalysisDao()
    }

    @Provides
    fun provideChatDao(database: ExpenseDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    @Singleton
    fun provideChatRepository(chatDao: ChatDao): ChatRepository {
        return ChatRepository(chatDao)
    }
}
