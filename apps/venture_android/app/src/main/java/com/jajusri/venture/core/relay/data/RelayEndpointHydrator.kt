package com.jajusri.venture.core.relay.data

import com.jajusri.venture.core.relay.data.local.RelayEndpointLocalStore
import com.jajusri.venture.core.relay.data.remote.RelayRuntimeEndpointProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Loads the persisted Relay base URL into the in-memory provider at process start -- exact shape
 * of `core/trust/data/TrustEndpointHydrator.kt` / `core/startup/ConnectorBaseUrlHydrator.kt`. */
interface RelayEndpointHydrator {
    suspend fun hydrate()
}

@Singleton
class DefaultRelayEndpointHydrator @Inject constructor(
    private val localStore: RelayEndpointLocalStore,
    private val provider: RelayRuntimeEndpointProvider,
) : RelayEndpointHydrator {
    override suspend fun hydrate() {
        provider.updateInMemory(localStore.read())
    }
}
