package com.jajusri.venture.core.connection.di

import android.content.Context
import android.net.nsd.NsdManager
import com.jajusri.venture.core.connection.ConnectorConnectionOrchestrator
import com.jajusri.venture.core.connection.ConnectorConnectionResolver
import com.jajusri.venture.core.connection.ConnectorEnrolmentGate
import com.jajusri.venture.core.connection.ConnectorEnrolmentService
import com.jajusri.venture.core.connection.ConnectorHealthProbe
import com.jajusri.venture.core.connection.ConnectorReconnectCoordinator
import com.jajusri.venture.core.connection.DefaultConnectorConnectionOrchestrator
import com.jajusri.venture.core.connection.DefaultConnectorConnectionResolver
import com.jajusri.venture.core.connection.DefaultConnectorEnrolmentGate
import com.jajusri.venture.core.connection.DefaultConnectorEnrolmentService
import com.jajusri.venture.core.connection.DefaultConnectorReconnectCoordinator
import com.jajusri.venture.core.connection.DefaultExistingUrlMigrationService
import com.jajusri.venture.core.connection.ExistingUrlMigrationService
import com.jajusri.venture.core.connection.OkHttpConnectorHealthProbe
import com.jajusri.venture.core.connection.data.local.PairedConnectorLocalDataSource
import com.jajusri.venture.core.connection.data.local.RoomPairedConnectorLocalDataSource
import com.jajusri.venture.core.discovery.ConnectorDiscoveryPort
import com.jajusri.venture.core.discovery.MulticastLockController
import com.jajusri.venture.core.discovery.NsdConnectorDiscoveryService
import com.jajusri.venture.core.discovery.WifiMulticastLockController
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
    abstract fun bindMulticastLockController(impl: WifiMulticastLockController): MulticastLockController

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
