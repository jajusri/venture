package com.budcom.android.feature.serverconfig.data.di

import com.budcom.android.feature.serverconfig.data.local.ConnectorBaseUrlLocalDataSource
import com.budcom.android.feature.serverconfig.data.local.ConnectorBaseUrlLocalStore
import com.budcom.android.feature.serverconfig.data.remote.ConnectorHealthRemoteDataSource
import com.budcom.android.feature.serverconfig.data.remote.ConnectorSystemApi
import com.budcom.android.feature.serverconfig.data.remote.DefaultConnectorHealthRemoteDataSource
import com.budcom.android.feature.serverconfig.data.repository.ConnectorConfigRepositoryImpl
import com.budcom.android.feature.serverconfig.domain.repository.ConnectorConfigRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServerConfigBindModule {

    @Binds
    @Singleton
    abstract fun bindConnectorConfigRepository(
        impl: ConnectorConfigRepositoryImpl,
    ): ConnectorConfigRepository

    @Binds
    @Singleton
    abstract fun bindConnectorStatusPort(
        impl: ConnectorConfigRepositoryImpl,
    ): com.budcom.android.feature.serverconfig.domain.port.ConnectorStatusPort

    @Binds
    @Singleton
    abstract fun bindBaseUrlLocalStore(
        impl: ConnectorBaseUrlLocalDataSource,
    ): ConnectorBaseUrlLocalStore

    @Binds
    @Singleton
    abstract fun bindConnectorBaseUrlHydrator(
        impl: com.budcom.android.feature.serverconfig.data.startup.DefaultConnectorBaseUrlHydrator,
    ): com.budcom.android.core.startup.ConnectorBaseUrlHydrator

    @Binds
    @Singleton
    abstract fun bindHealthRemoteDataSource(
        impl: DefaultConnectorHealthRemoteDataSource,
    ): ConnectorHealthRemoteDataSource
}

@Module
@InstallIn(SingletonComponent::class)
object ServerConfigProvideModule {

    @Provides
    @Singleton
    fun provideConnectorSystemApi(retrofit: Retrofit): ConnectorSystemApi =
        retrofit.create(ConnectorSystemApi::class.java)
}
