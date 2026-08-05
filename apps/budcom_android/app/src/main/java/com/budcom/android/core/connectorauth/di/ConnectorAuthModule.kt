package com.budcom.android.core.connectorauth.di

import com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.budcom.android.core.connectorauth.data.remote.OkHttpAuthenticatedConnectorApiClient
import com.budcom.android.core.connectorauth.domain.AuthenticatedConnectorContextProvider
import com.budcom.android.core.connectorauth.domain.DefaultAuthenticatedConnectorContextProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindings for the dormant authenticated business-client foundation (Phase 3R). Nothing here is
 * injected into any repository, ViewModel, or navigation destination yet — see the Phase 3R
 * evidence report. Construction alone performs no network request and starts no background work.
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
}
