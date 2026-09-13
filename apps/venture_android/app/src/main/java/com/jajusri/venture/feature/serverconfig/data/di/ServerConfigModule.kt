package com.jajusri.venture.feature.serverconfig.data.di

import com.jajusri.venture.feature.serverconfig.data.local.ConnectorBaseUrlLocalDataSource
import com.jajusri.venture.feature.serverconfig.data.local.ConnectorBaseUrlLocalStore
import com.jajusri.venture.feature.serverconfig.data.remote.ConnectorHealthRemoteDataSource
import com.jajusri.venture.feature.serverconfig.data.remote.ConnectorSystemApi
import com.jajusri.venture.feature.serverconfig.data.remote.DefaultConnectorHealthRemoteDataSource
import com.jajusri.venture.feature.serverconfig.data.repository.ConnectorConfigRepositoryImpl
import com.jajusri.venture.feature.serverconfig.data.repository.ConnectorOperationalStatusPortImpl
import com.jajusri.venture.feature.serverconfig.domain.port.ConnectorOperationalStatusPort
import com.jajusri.venture.feature.serverconfig.domain.repository.ConnectorConfigRepository
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
    ): com.jajusri.venture.feature.serverconfig.domain.port.ConnectorStatusPort

    @Binds
    @Singleton
    abstract fun bindBaseUrlLocalStore(
        impl: ConnectorBaseUrlLocalDataSource,
    ): ConnectorBaseUrlLocalStore

    @Binds
    @Singleton
    abstract fun bindConnectorBaseUrlHydrator(
        impl: com.jajusri.venture.feature.serverconfig.data.startup.DefaultConnectorBaseUrlHydrator,
    ): com.jajusri.venture.core.startup.ConnectorBaseUrlHydrator

    @Binds
    @Singleton
    abstract fun bindHealthRemoteDataSource(
        impl: DefaultConnectorHealthRemoteDataSource,
    ): ConnectorHealthRemoteDataSource

    /** TD-016: transport-aware successor consulted wherever a securely paired device's real
     * connection status must be shown instead of the always-LEGACY [ConnectorStatusPort]. */
    @Binds
    @Singleton
    abstract fun bindConnectorOperationalStatusPort(
        impl: ConnectorOperationalStatusPortImpl,
    ): ConnectorOperationalStatusPort
}

@Module
@InstallIn(SingletonComponent::class)
object ServerConfigProvideModule {

    @Provides
    @Singleton
    fun provideConnectorSystemApi(retrofit: Retrofit): ConnectorSystemApi =
        retrofit.create(ConnectorSystemApi::class.java)
}
