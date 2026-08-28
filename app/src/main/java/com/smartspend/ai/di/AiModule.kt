package com.smartspend.ai.di

import com.smartspend.ai.ai.gateway.AiGateway
import com.smartspend.ai.ai.gateway.FirebaseAiGateway
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
