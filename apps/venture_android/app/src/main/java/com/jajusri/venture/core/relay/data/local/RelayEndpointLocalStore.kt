package com.jajusri.venture.core.relay.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jajusri.venture.feature.serverconfig.domain.validation.ConnectorUrlValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Local persistence for the Relay base URL -- own DataStore file, deliberately distinct from
 * Trust's and the Connector's. Validates via the same generic [ConnectorUrlValidator] both other
 * runtime-configurable endpoints already use. */
interface RelayEndpointLocalStore {
    val baseUrl: Flow<String?>
    suspend fun configure(rawUrl: String): Boolean
    suspend fun read(): String?
    suspend fun clear()
}

private val Context.relayEndpointDataStore: DataStore<Preferences> by preferencesDataStore(name = "relay_endpoint_config")

@Singleton
class DataStoreRelayEndpointLocalStore @Inject constructor(
    @ApplicationContext context: Context,
) : RelayEndpointLocalStore {
    private val dataStore = context.relayEndpointDataStore
    private val preferences = dataStore.data.catch { exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }

    override val baseUrl: Flow<String?> = preferences.map { it[KEY_BASE_URL] }

    override suspend fun configure(rawUrl: String): Boolean {
        val normalized = ConnectorUrlValidator.normalizeOrNull(rawUrl) ?: return false
        dataStore.edit { prefs -> prefs[KEY_BASE_URL] = normalized }
        return true
    }

    override suspend fun read(): String? = baseUrl.first()

    override suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(KEY_BASE_URL) }
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("relay_base_url")
    }
}
