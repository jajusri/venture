package com.jajusri.venture.core.relay.data.remote

import com.jajusri.venture.feature.transaction.domain.port.RelayEndpointProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real runtime-configurable implementation of the (protected, `feature/transaction`-owned)
 * `RelayEndpointProvider` port -- replaces `TransactionModule.kt`'s previous
 * `BuildConfig.RELAY_DEFAULT_BASE_URL` wiring (Gate 4 completion). Pure in-memory
 * (`AtomicReference`/`StateFlow`), exact shape of `core/network/ConnectorBaseUrlProvider.kt` and
 * `core/trust/data/remote/TrustEndpointProvider.kt`: `HttpRelayClient` reads `snapshot()` directly
 * (no interceptor needed here -- unlike Trust's Retrofit stack, `HttpRelayClient` builds absolute
 * URLs by string concatenation and already treats a `null` snapshot as "endpoint unconfigured" at
 * every call site, so this class only has to supply that value correctly).
 *
 * Deliberately its OWN persisted store/hydrator ([RelayEndpointLocalStore], [RelayEndpointHydrator])
 * -- kept fully distinct from [com.jajusri.venture.core.trust.data.remote.TrustEndpointProvider] and
 * from the Connector's own `ConnectorBaseUrlProvider`, per this round's explicit requirement that
 * Trust/Relay/Connector endpoints never share configuration. Default is UNCONFIGURED (`null`) in
 * every build variant -- no `BuildConfig` fallback of any kind.
 */
interface RelayRuntimeEndpointProvider : RelayEndpointProvider {
    fun observe(): Flow<String?>
    fun updateInMemory(normalizedBaseUrl: String?)
}

@Singleton
class DefaultRelayRuntimeEndpointProvider @Inject constructor() : RelayRuntimeEndpointProvider {
    private val cached = AtomicReference<String?>(null)
    private val flow = MutableStateFlow<String?>(null)

    override fun snapshot(): String? = cached.get()
    override fun observe(): Flow<String?> = flow.asStateFlow()
    override fun updateInMemory(normalizedBaseUrl: String?) {
        cached.set(normalizedBaseUrl)
        flow.update { normalizedBaseUrl }
    }
}
