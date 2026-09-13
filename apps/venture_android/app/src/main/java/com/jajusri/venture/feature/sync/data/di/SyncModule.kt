package com.jajusri.venture.feature.sync.data.di

import com.jajusri.venture.core.network.SyncHttp
import com.jajusri.venture.feature.sync.data.remote.AuthenticatedSyncRemoteDataSource
import com.jajusri.venture.feature.sync.data.remote.DefaultAuthenticatedSyncRemoteDataSource
import com.jajusri.venture.feature.sync.data.remote.DefaultSyncRemoteDataSource
import com.jajusri.venture.feature.sync.data.remote.SyncApi
import com.jajusri.venture.feature.sync.data.remote.SyncRemoteDataSource
import com.jajusri.venture.feature.sync.data.repository.SyncRepositoryImpl
import com.jajusri.venture.feature.sync.domain.port.ObserveSyncStatusPort
import com.jajusri.venture.feature.sync.domain.repository.SyncRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindModule {
    @Binds
    @Singleton
    abstract fun bindSyncRemoteDataSource(
        impl: DefaultSyncRemoteDataSource,
    ): SyncRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindAuthenticatedSyncRemoteDataSource(
        impl: DefaultAuthenticatedSyncRemoteDataSource,
    ): AuthenticatedSyncRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindSyncRepository(
        impl: SyncRepositoryImpl,
    ): SyncRepository

    @Binds
    @Singleton
    abstract fun bindObserveSyncStatusPort(
        impl: SyncRepositoryImpl,
    ): ObserveSyncStatusPort
}

@Module
@InstallIn(SingletonComponent::class)
object SyncProvideModule {
    @Provides
    @Singleton
    fun provideSyncApi(@SyncHttp retrofit: Retrofit): SyncApi =
        retrofit.create(SyncApi::class.java)
}
