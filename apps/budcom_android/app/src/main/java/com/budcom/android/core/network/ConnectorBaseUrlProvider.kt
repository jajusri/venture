package com.budcom.android.core.network

import com.budcom.android.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe source of the active Connector base URL for OkHttp/Retrofit.
 *
 * Retrofit is constructed once; [DynamicBaseUrlInterceptor] reads [snapshot] per request.
 * Persistence is owned by the server-config feature; this store never silently reverts a
 * user-saved value back to [DEFAULT].
 */
interface ConnectorBaseUrlProvider {
    /** Current normalized base URL ending with `/`. */
    fun snapshot(): String

    /** Parsed [HttpUrl] for the current snapshot. */
    fun snapshotHttpUrl(): HttpUrl

    /** Observable stream of normalized base URLs. */
    fun observe(): Flow<String>

    /**
     * Replaces the in-memory URL used by the interceptor.
     * Caller must persist separately when the change should survive process death.
     */
    fun updateInMemory(normalizedBaseUrl: String)
}

/**
 * Default in-memory provider seeded from [BuildConfig.CONNECTOR_BASE_URL].
 */
@Singleton
class DefaultConnectorBaseUrlProvider @Inject constructor() : ConnectorBaseUrlProvider {

    private val cached = AtomicReference(DEFAULT)
    private val flow = MutableStateFlow(DEFAULT)

    override fun snapshot(): String = cached.get()

    override fun snapshotHttpUrl(): HttpUrl = snapshot().toHttpUrl()

    override fun observe(): Flow<String> = flow.asStateFlow()

    override fun updateInMemory(normalizedBaseUrl: String) {
        cached.set(normalizedBaseUrl)
        flow.update { normalizedBaseUrl }
    }

    companion object {
        const val DEFAULT: String = BuildConfig.CONNECTOR_BASE_URL
    }
}
