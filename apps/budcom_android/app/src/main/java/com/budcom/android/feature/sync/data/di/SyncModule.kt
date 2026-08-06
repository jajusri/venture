package com.budcom.android.feature.sync.data.di

import com.budcom.android.core.network.SyncHttp
import com.budcom.android.feature.sync.data.remote.AuthenticatedSyncRemoteDataSource
import com.budcom.android.feature.sync.data.remote.DefaultAuthenticatedSyncRemoteDataSource
import com.budcom.android.feature.sync.data.remote.DefaultSyncRemoteDataSource
import com.budcom.android.feature.sync.data.remote.SyncApi
import com.budcom.android.feature.sync.data.remote.SyncRemoteDataSource
import com.budcom.android.feature.sync.data.repository.SyncRepositoryImpl
import com.budcom.android.feature.sync.domain.port.ObserveSyncStatusPort
import com.budcom.android.feature.sync.domain.repository.SyncRepository
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
