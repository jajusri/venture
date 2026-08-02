package com.budcom.android.core.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a single bounded-timeout `GET /health` probe against an explicit host:port. */
data class ConnectorHealthProbeResult(
    val reachable: Boolean,
    val connectorId: String?,
    val connectorName: String?,
)

/**
 * Bounded-timeout probe of an arbitrary Connector host:port's `/health` endpoint.
 *
 * Deliberately independent of the app's shared Retrofit/OkHttp stack (which is bound to
 * [com.budcom.android.core.network.ConnectorBaseUrlProvider]'s *current* base URL): the
 * connection resolver needs to probe a *candidate* endpoint (last-known or freshly
 * discovered) without perturbing global network state until that candidate is confirmed.
 */
interface ConnectorHealthProbe {
    suspend fun probe(host: String, port: Int, timeoutMs: Long): ConnectorHealthProbeResult
}

@Serializable
private data class MinimalHealthDto(
    val connectorId: String? = null,
    val connectorName: String? = null,
)

@Singleton
class OkHttpConnectorHealthProbe @Inject constructor() : ConnectorHealthProbe {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun probe(host: String, port: Int, timeoutMs: Long): ConnectorHealthProbeResult =
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
            val request = Request.Builder()
                .url("http://$host:$port/health")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext ConnectorHealthProbeResult(false, null, null)
                    }
                    val body = response.body?.string().orEmpty()
                    val dto = runCatching {
                        json.decodeFromString(MinimalHealthDto.serializer(), body)
                    }.getOrNull()
                    ConnectorHealthProbeResult(
                        reachable = dto != null,
                        connectorId = dto?.connectorId,
                        connectorName = dto?.connectorName,
                    )
                }
            } catch (e: IOException) {
                ConnectorHealthProbeResult(false, null, null)
            }
        }
}
