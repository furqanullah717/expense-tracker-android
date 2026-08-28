package com.smartspend.ai.di

import android.content.Context
import com.smartspend.ai.data.ExpenseDatabase
import com.smartspend.ai.data.dao.AiAnalysisDao
import com.smartspend.ai.data.dao.ChatDao
import com.smartspend.ai.data.dao.ExpenseDao
import com.smartspend.ai.data.repository.ChatRepository
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
