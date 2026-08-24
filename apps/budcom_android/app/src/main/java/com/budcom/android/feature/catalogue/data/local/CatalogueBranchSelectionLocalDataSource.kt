package com.budcom.android.feature.catalogue.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.budcom.android.feature.catalogue.domain.port.CatalogueBranchSelectionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.catalogueBranchSelectionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "catalogue_branch_selection",
)

/**
 * DataStore-backed [CatalogueBranchSelectionStore], mirroring
 * [com.budcom.android.feature.masterdata.ledger.sharing.LedgerSharingPreferencesLocalDataSource]'s
 * exact pattern (own store file, safe fallback on a corrupt/missing read). One key per company —
 * `stringPreferencesKey` is a plain string wrapper, so a per-`companyId` key is legitimate and is
 * what makes cross-company isolation structural here rather than convention-only: reading company
 * B's key can never return company A's stored value because they are different keys entirely.
 */
@Singleton
class CatalogueBranchSelectionLocalDataSource @Inject constructor(
    @ApplicationContext context: Context,
) : CatalogueBranchSelectionStore {
    private val dataStore = context.catalogueBranchSelectionDataStore

    override fun observeSelectedBranchId(companyId: String): Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs -> prefs[keyFor(companyId)] }
        .distinctUntilChanged()

    override suspend fun setSelectedBranchId(companyId: String, branchId: String?) {
        dataStore.edit { prefs ->
            if (branchId == null) prefs.remove(keyFor(companyId)) else prefs[keyFor(companyId)] = branchId
        }
    }

    private fun keyFor(companyId: String) = stringPreferencesKey("selected_branch_$companyId")
}
