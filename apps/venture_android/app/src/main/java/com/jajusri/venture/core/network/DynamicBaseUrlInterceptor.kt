package com.jajusri.venture.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewrites each request's scheme/host/port to the configured Connector origin.
 *
 * Retrofit keeps a single instance; path and query from the relative endpoint are preserved.
 * Configured base URLs are origin-only (path `/`) after normalization.
 */
@Singleton
class DynamicBaseUrlInterceptor @Inject constructor(
    private val baseUrlProvider: ConnectorBaseUrlProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val configured = baseUrlProvider.snapshotHttpUrl()
            ?: throw IOException("Connector endpoint is not configured.")
        val rewritten = original.url.newBuilder()
            .scheme(configured.scheme)
            .host(configured.host)
            .port(configured.port)
            .build()
        return chain.proceed(original.newBuilder().url(rewritten).build())
    }
}
