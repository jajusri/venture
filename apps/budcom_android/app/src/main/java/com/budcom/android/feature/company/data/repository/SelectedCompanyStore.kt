package com.budcom.android.feature.company.data.repository

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

private val Context.companySelectionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "company_selection",
)

interface SelectedCompanyStore {
    fun observeSelectedCompanyId(): Flow<String?>
    suspend fun getSelectedCompanyId(): String?
    suspend fun saveSelectedCompanyId(companyId: String)
    suspend fun clearSelectedCompanyId()
}

@Singleton
class DataStoreSelectedCompanyStore @Inject constructor(
    @ApplicationContext context: Context,
) : SelectedCompanyStore {

    private val dataStore = context.companySelectionDataStore

    override fun observeSelectedCompanyId(): Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs -> prefs[KEY_SELECTED_COMPANY_ID] }

    override suspend fun getSelectedCompanyId(): String? = observeSelectedCompanyId().first()

    override suspend fun saveSelectedCompanyId(companyId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SELECTED_COMPANY_ID] = companyId
        }
    }

    override suspend fun clearSelectedCompanyId() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_SELECTED_COMPANY_ID)
        }
    }

    private companion object {
        val KEY_SELECTED_COMPANY_ID = stringPreferencesKey("selected_company_id")
    }
}
