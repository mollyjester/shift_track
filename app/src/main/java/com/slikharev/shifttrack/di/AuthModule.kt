package com.slikharev.shifttrack.di

import com.slikharev.shifttrack.auth.AuthRepository
import com.slikharev.shifttrack.auth.UserSession
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    @Singleton
    abstract fun bindUserSession(authRepository: AuthRepository): UserSession
}
