package com.jajusri.venture.core.relay.di

import com.jajusri.venture.core.relay.data.DefaultRelayEndpointHydrator
import com.jajusri.venture.core.relay.data.RelayEndpointHydrator
import com.jajusri.venture.core.relay.data.local.DataStoreRelayEndpointLocalStore
import com.jajusri.venture.core.relay.data.local.RelayEndpointLocalStore
import com.jajusri.venture.core.relay.data.remote.DefaultRelayRuntimeEndpointProvider
import com.jajusri.venture.core.relay.data.remote.RelayRuntimeEndpointProvider
import com.jajusri.venture.feature.transaction.domain.port.RelayEndpointProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Satisfies the (protected, `feature/transaction`-owned) `RelayEndpointProvider` port from OUTSIDE
 * that package -- `TransactionModule.kt`'s own previous `@Provides fun provideRelayEndpointProvider()`
 * (`BuildConfig.RELAY_DEFAULT_BASE_URL`) was removed in the same change that added this module, so
 * there is exactly one binding for this type, not two.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RelayConfigBindModule {
    @Binds
    @Singleton
    abstract fun bindRelayRuntimeEndpointProvider(impl: DefaultRelayRuntimeEndpointProvider): RelayRuntimeEndpointProvider

    @Binds
    @Singleton
    abstract fun bindRelayEndpointProvider(impl: RelayRuntimeEndpointProvider): RelayEndpointProvider

    @Binds
    @Singleton
    abstract fun bindRelayEndpointLocalStore(impl: DataStoreRelayEndpointLocalStore): RelayEndpointLocalStore

    @Binds
    @Singleton
    abstract fun bindRelayEndpointHydrator(impl: DefaultRelayEndpointHydrator): RelayEndpointHydrator
}
