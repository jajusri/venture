package com.budcom.android.core.trust.data

import com.budcom.android.core.trust.data.local.TrustEndpointLocalStore
import com.budcom.android.core.trust.data.remote.TrustEndpointProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Loads the persisted Trust base URL into the in-memory provider at process start -- exact shape
 * of `core/startup/ConnectorBaseUrlHydrator.kt`. Called from `BudcomApplication.onCreate()`. */
interface TrustEndpointHydrator {
    suspend fun hydrate()
}

@Singleton
class DefaultTrustEndpointHydrator @Inject constructor(
    private val localStore: TrustEndpointLocalStore,
    private val provider: TrustEndpointProvider,
) : TrustEndpointHydrator {
    override suspend fun hydrate() {
        provider.updateInMemory(localStore.read())
    }
}
