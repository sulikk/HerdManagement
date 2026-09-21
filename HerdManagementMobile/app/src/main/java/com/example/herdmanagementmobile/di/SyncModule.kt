package com.example.herdmanagementmobile.di

import com.example.herdmanagementmobile.data.sync.SyncMarkerStore
import com.example.herdmanagementmobile.data.sync.SyncStateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Wiąże abstrakcję znacznika synchronizacji z implementacją na DataStore. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @Singleton
    abstract fun bindSyncMarkerStore(impl: SyncStateRepository): SyncMarkerStore
}
