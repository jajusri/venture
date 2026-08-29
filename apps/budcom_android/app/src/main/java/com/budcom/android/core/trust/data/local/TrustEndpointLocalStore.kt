package com.budcom.android.core.trust.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Local persistence for the Trust base URL -- exact shape of
 * `feature/serverconfig/data/local/ConnectorBaseUrlLocalStore.kt`. */
interface TrustEndpointLocalStore {
    val baseUrl: Flow<String?>
    suspend fun save(normalizedBaseUrl: String)
    suspend fun read(): String?
    suspend fun clear()
}

private val Context.trustEndpointDataStore: DataStore<Preferences> by preferencesDataStore(name = "trust_endpoint_config")

@Singleton
class DataStoreTrustEndpointLocalStore @Inject constructor(
    @ApplicationContext context: Context,
) : TrustEndpointLocalStore {
    private val dataStore = context.trustEndpointDataStore
    private val preferences = dataStore.data.catch { exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }

    override val baseUrl: Flow<String?> = preferences.map { it[KEY_BASE_URL] }

    override suspend fun save(normalizedBaseUrl: String) {
        dataStore.edit { prefs -> prefs[KEY_BASE_URL] = normalizedBaseUrl }
    }

    override suspend fun read(): String? = baseUrl.first()

    override suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(KEY_BASE_URL) }
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("trust_base_url")
    }
}
