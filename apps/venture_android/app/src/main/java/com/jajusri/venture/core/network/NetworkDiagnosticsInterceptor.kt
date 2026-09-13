package com.jajusri.venture.core.network

import com.jajusri.venture.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privacy-safe development diagnostics for Connector requests.
 *
 * Logs origin/path, timing, status and response metadata only. Query values,
 * headers, bodies, credentials and company data are never logged.
 */
@Singleton
class NetworkDiagnosticsInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedNanos = System.nanoTime()
        val safeUrl = "${request.url.scheme}://${request.url.host}:${request.url.port}${request.url.encodedPath}"

        if (BuildConfig.NETWORK_LOGGING_ENABLED) {
            Timber.tag(TAG).d(
                "request started method=%s url=%s timeoutMs=%d",
                request.method,
                safeUrl,
                chain.call().timeout().timeoutNanos().let {
                    if (it == 0L) 0L else TimeUnit.NANOSECONDS.toMillis(it)
                },
            )
        }

        return try {
            val response = chain.proceed(request)
            if (BuildConfig.NETWORK_LOGGING_ENABLED) {
                Timber.tag(TAG).d(
                    "response status=%d durationMs=%d contentType=%s contentLength=%d",
                    response.code,
                    elapsedMillis(startedNanos),
                    response.body?.contentType()?.toString() ?: "unknown",
                    response.body?.contentLength() ?: -1L,
                )
            }
            response
        } catch (error: Throwable) {
            if (BuildConfig.NETWORK_LOGGING_ENABLED) {
                Timber.tag(TAG).e(
                    error,
                    "request failed durationMs=%d exceptionType=%s",
                    elapsedMillis(startedNanos),
                    error.javaClass.simpleName,
                )
            }
            throw error
        }
    }

    private fun elapsedMillis(startedNanos: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)

    private companion object {
        const val TAG = "ConnectorNetwork"
    }
}
