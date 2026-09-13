package com.jajusri.venture.feature.company.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import com.jajusri.venture.feature.company.domain.model.SessionSelectedCompany
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
    fun observeSelectedCompany(): Flow<SessionSelectedCompany?> =
        observeSelectedCompanyId().map { id -> id?.let { SessionSelectedCompany(it, it) } }
    fun observeSelectedCompanyId(): Flow<String?>
    suspend fun getSelectedCompanyId(): String?
    suspend fun saveSelectedCompany(company: SessionSelectedCompany) = saveSelectedCompanyId(company.id)
    suspend fun saveSelectedCompanyIfCurrentId(
        expectedCompanyId: String?,
        company: SessionSelectedCompany,
    ): Boolean {
        if (getSelectedCompanyId() != expectedCompanyId) return false
        saveSelectedCompany(company)
        return true
    }
    suspend fun saveSelectedCompanyId(companyId: String)
    suspend fun clearSelectedCompanyIfCurrentId(expectedCompanyId: String): Boolean {
        if (getSelectedCompanyId() != expectedCompanyId) return false
        clearSelectedCompanyId()
        return true
    }
    suspend fun clearSelectedCompanyId()
}

@Singleton
class DataStoreSelectedCompanyStore @Inject constructor(
    @ApplicationContext context: Context,
) : SelectedCompanyStore {

    private val dataStore = context.companySelectionDataStore

    private val preferences = dataStore.data.catch { exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }

    override fun observeSelectedCompany(): Flow<SessionSelectedCompany?> = preferences
        .map { prefs ->
            val id = prefs[KEY_SELECTED_COMPANY_ID]
            val name = prefs[KEY_SELECTED_COMPANY_NAME]
            if (id != null && name != null) SessionSelectedCompany(id, name) else null
        }

    // Read the ID independently so an existing ID-only preference remains available
    // while its name is being resolved and migrated.
    override fun observeSelectedCompanyId(): Flow<String?> = preferences
        .map { prefs -> prefs[KEY_SELECTED_COMPANY_ID] }

    override suspend fun getSelectedCompanyId(): String? = observeSelectedCompanyId().first()

    override suspend fun saveSelectedCompanyId(companyId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SELECTED_COMPANY_ID] = companyId
        }
    }

    override suspend fun saveSelectedCompany(company: SessionSelectedCompany) {
        dataStore.edit { prefs ->
            prefs[KEY_SELECTED_COMPANY_ID] = company.id
            prefs[KEY_SELECTED_COMPANY_NAME] = company.name
        }
    }

    override suspend fun saveSelectedCompanyIfCurrentId(
        expectedCompanyId: String?,
        company: SessionSelectedCompany,
    ): Boolean {
        var saved = false
        dataStore.edit { prefs ->
            if (prefs[KEY_SELECTED_COMPANY_ID] == expectedCompanyId) {
                prefs[KEY_SELECTED_COMPANY_ID] = company.id
                prefs[KEY_SELECTED_COMPANY_NAME] = company.name
                saved = true
            }
        }
        return saved
    }

    override suspend fun clearSelectedCompanyId() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_SELECTED_COMPANY_ID)
            prefs.remove(KEY_SELECTED_COMPANY_NAME)
        }
    }

    override suspend fun clearSelectedCompanyIfCurrentId(expectedCompanyId: String): Boolean {
        var cleared = false
        dataStore.edit { prefs ->
            if (prefs[KEY_SELECTED_COMPANY_ID] == expectedCompanyId) {
                prefs.remove(KEY_SELECTED_COMPANY_ID)
                prefs.remove(KEY_SELECTED_COMPANY_NAME)
                cleared = true
            }
        }
        return cleared
    }

    private companion object {
        val KEY_SELECTED_COMPANY_ID = stringPreferencesKey("selected_company_id")
        val KEY_SELECTED_COMPANY_NAME = stringPreferencesKey("selected_company_name")
    }
}
