package com.budcom.android.core.connection.di

import android.content.Context
import android.net.nsd.NsdManager
import com.budcom.android.core.connection.ConnectorConnectionOrchestrator
import com.budcom.android.core.connection.ConnectorConnectionResolver
import com.budcom.android.core.connection.ConnectorEnrolmentGate
import com.budcom.android.core.connection.ConnectorEnrolmentService
import com.budcom.android.core.connection.ConnectorHealthProbe
import com.budcom.android.core.connection.ConnectorReconnectCoordinator
import com.budcom.android.core.connection.DefaultConnectorConnectionOrchestrator
import com.budcom.android.core.connection.DefaultConnectorConnectionResolver
import com.budcom.android.core.connection.DefaultConnectorEnrolmentGate
import com.budcom.android.core.connection.DefaultConnectorEnrolmentService
import com.budcom.android.core.connection.DefaultConnectorReconnectCoordinator
import com.budcom.android.core.connection.DefaultExistingUrlMigrationService
import com.budcom.android.core.connection.ExistingUrlMigrationService
import com.budcom.android.core.connection.OkHttpConnectorHealthProbe
import com.budcom.android.core.connection.data.local.PairedConnectorLocalDataSource
import com.budcom.android.core.connection.data.local.RoomPairedConnectorLocalDataSource
import com.budcom.android.core.discovery.ConnectorDiscoveryPort
import com.budcom.android.core.discovery.NsdConnectorDiscoveryService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectionBindModule {

    @Binds
    @Singleton
    abstract fun bindPairedConnectorLocalDataSource(
        impl: RoomPairedConnectorLocalDataSource,
    ): PairedConnectorLocalDataSource

    @Binds
    @Singleton
    abstract fun bindConnectorHealthProbe(impl: OkHttpConnectorHealthProbe): ConnectorHealthProbe

    @Binds
    @Singleton
    abstract fun bindConnectorDiscoveryPort(impl: NsdConnectorDiscoveryService): ConnectorDiscoveryPort

    @Binds
    @Singleton
    abstract fun bindConnectorConnectionResolver(
        impl: DefaultConnectorConnectionResolver,
    ): ConnectorConnectionResolver

    @Binds
    @Singleton
    abstract fun bindExistingUrlMigrationService(
        impl: DefaultExistingUrlMigrationService,
    ): ExistingUrlMigrationService

    @Binds
    @Singleton
    abstract fun bindConnectorConnectionOrchestrator(
        impl: DefaultConnectorConnectionOrchestrator,
    ): ConnectorConnectionOrchestrator

    @Binds
    @Singleton
    abstract fun bindConnectorReconnectCoordinator(
        impl: DefaultConnectorReconnectCoordinator,
    ): ConnectorReconnectCoordinator

    @Binds
    @Singleton
    abstract fun bindConnectorEnrolmentGate(
        impl: DefaultConnectorEnrolmentGate,
    ): ConnectorEnrolmentGate

    @Binds
    @Singleton
    abstract fun bindConnectorEnrolmentService(
        impl: DefaultConnectorEnrolmentService,
    ): ConnectorEnrolmentService
}

@Module
@InstallIn(SingletonComponent::class)
object ConnectionProvideModule {

    @Provides
    @Singleton
    fun provideNsdManager(@ApplicationContext context: Context): NsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
}
