package com.codewithfk.expensetracker.android.di

import com.codewithfk.expensetracker.android.ai.core.AiGateway
import com.codewithfk.expensetracker.android.ai.core.FirebaseAiGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindAiGateway(
        firebaseAiGateway: FirebaseAiGateway
    ): AiGateway
}
