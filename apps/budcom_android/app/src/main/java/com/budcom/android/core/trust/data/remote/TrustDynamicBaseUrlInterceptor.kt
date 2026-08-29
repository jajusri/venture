package com.budcom.android.core.trust.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Exact shape of `core/network/DynamicBaseUrlInterceptor.kt`, against [TrustEndpointProvider]
 * instead of the Connector's own provider -- kept as a separate interceptor/client rather than
 * reusing the shared Connector OkHttp stack because Trust is a distinct service with its own
 * endpoint and must fail closed (throw, never silently fall back to the Connector's origin) when
 * unconfigured. */
@Singleton
class TrustDynamicBaseUrlInterceptor @Inject constructor(
    private val endpointProvider: TrustEndpointProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val configured = endpointProvider.snapshot()?.toHttpUrlOrNull()
            ?: throw IOException("Trust endpoint is not configured.")
        val rewritten = original.url.newBuilder()
            .scheme(configured.scheme)
            .host(configured.host)
            .port(configured.port)
            .build()
        return chain.proceed(original.newBuilder().url(rewritten).build())
    }
}
