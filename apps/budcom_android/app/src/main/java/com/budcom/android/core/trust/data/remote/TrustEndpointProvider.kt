package com.budcom.android.core.trust.data.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe, PURE IN-MEMORY source of the active Trust base URL, read synchronously by
 * `TrustHttpModule`'s OkHttp interceptor -- exact shape of `ConnectorBaseUrlProvider`
 * (`core/network/ConnectorBaseUrlProvider.kt`), which documents why this must stay in-memory-only:
 * an interceptor runs on OkHttp's dispatcher thread, not a coroutine, so it cannot suspend to read
 * DataStore. Persistence is a SEPARATE concern ([TrustEndpointLocalStore]), loaded into this
 * provider once at process startup by [TrustEndpointHydrator] (see `BudcomApplication.onCreate()`).
 *
 * Default is UNCONFIGURED (`null`) in every build variant -- deliberately NOT a `BuildConfig`
 * constant (Gate 4: "no hardcoded office/home IP in production source"; "production-safe default =
 * unavailable/fail closed"). Every caller must already treat `null` as "Trust unavailable."
 */
interface TrustEndpointProvider {
    fun snapshot(): String?
    fun observe(): Flow<String?>
    fun updateInMemory(normalizedBaseUrl: String?)
}

@Singleton
class DefaultTrustEndpointProvider @Inject constructor() : TrustEndpointProvider {
    private val cached = AtomicReference<String?>(null)
    private val flow = MutableStateFlow<String?>(null)

    override fun snapshot(): String? = cached.get()
    override fun observe(): Flow<String?> = flow.asStateFlow()
    override fun updateInMemory(normalizedBaseUrl: String?) {
        cached.set(normalizedBaseUrl)
        flow.update { normalizedBaseUrl }
    }
}
