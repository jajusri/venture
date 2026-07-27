package com.budcom.android.feature.serverconfig.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.budcom.android.core.network.DefaultConnectorBaseUrlProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.connectorConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "connector_config",
)

/**
 * DataStore-backed persistence for the Connector base URL.
 */
@Singleton
class ConnectorBaseUrlLocalDataSource @Inject constructor(
    @ApplicationContext context: Context,
) : ConnectorBaseUrlLocalStore {

    private val dataStore = context.connectorConfigDataStore

    override val baseUrl: Flow<String> = dataStore.data
        .map { prefs ->
            prefs[KEY_BASE_URL] ?: DefaultConnectorBaseUrlProvider.DEFAULT
        }
        .distinctUntilChanged()

    override suspend fun save(normalizedBaseUrl: String) {
        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = normalizedBaseUrl
        }
    }

    override suspend fun read(): String = baseUrl.first()

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("connector_base_url")
    }
}
