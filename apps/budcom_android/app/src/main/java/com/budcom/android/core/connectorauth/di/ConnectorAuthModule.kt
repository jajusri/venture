package com.budcom.android.core.connectorauth.di

import com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.budcom.android.core.connectorauth.data.remote.OkHttpAuthenticatedConnectorApiClient
import com.budcom.android.core.connectorauth.domain.AuthenticatedConnectorContextProvider
import com.budcom.android.core.connectorauth.domain.AuthenticatedRepositoryFailurePolicy
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.budcom.android.core.connectorauth.domain.DefaultAuthenticatedConnectorContextProvider
import com.budcom.android.core.connectorauth.domain.DefaultAuthenticatedRepositoryFailurePolicy
import com.budcom.android.core.connectorauth.domain.DefaultConnectorTransportSelectionGate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindings for the authenticated business-client foundation (Phase 3R) plus the shared
 * transport-selection gate and rejection policy every repository cutover consults (Phase 3S-A
 * onward). Nothing here is injected into any ViewModel or navigation destination.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectorAuthBindModule {

    @Binds
    @Singleton
    abstract fun bindAuthenticatedConnectorContextProvider(
        impl: DefaultAuthenticatedConnectorContextProvider,
    ): AuthenticatedConnectorContextProvider

    @Binds
    @Singleton
    abstract fun bindAuthenticatedConnectorApiPort(
        impl: OkHttpAuthenticatedConnectorApiClient,
    ): AuthenticatedConnectorApiPort

    @Binds
    @Singleton
    abstract fun bindConnectorTransportSelectionGate(
        impl: DefaultConnectorTransportSelectionGate,
    ): ConnectorTransportSelectionGate

    @Binds
    @Singleton
    abstract fun bindAuthenticatedRepositoryFailurePolicy(
        impl: DefaultAuthenticatedRepositoryFailurePolicy,
    ): AuthenticatedRepositoryFailurePolicy
}
