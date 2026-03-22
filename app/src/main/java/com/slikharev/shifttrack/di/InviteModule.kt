package com.slikharev.shifttrack.di

import com.slikharev.shifttrack.invite.FirestoreInviteRepository
import com.slikharev.shifttrack.invite.InviteRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InviteModule {

    @Binds
    @Singleton
    abstract fun bindInviteRepository(impl: FirestoreInviteRepository): InviteRepository
}
