package com.budcom.android.feature.serverconfig.data.local

import kotlinx.coroutines.flow.Flow

/**
 * Local persistence for the Connector base URL.
 */
interface ConnectorBaseUrlLocalStore {
    val baseUrl: Flow<String>
    suspend fun save(normalizedBaseUrl: String)
    suspend fun read(): String
}
