package com.jajusri.venture.feature.masterdata.ledger.sharing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatementMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.ledgerSharingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ledger_sharing_settings",
)

/**
 * DataStore-backed Ledger Sharing preferences. Own store file, separate from `app_settings`
 * (theme) — same technology/pattern, not the same underlying file, so this feature has no
 * dependency on `feature.settings`'s internals. An unrecognized/corrupt stored value for any one
 * field falls back to that field's own default rather than discarding the whole preference set.
 */
@Singleton
class LedgerSharingPreferencesLocalDataSource @Inject constructor(
    @ApplicationContext context: Context,
) : LedgerSharingPreferencesStore {
    private val dataStore = context.ledgerSharingDataStore

    override val observation: Flow<LedgerSharingPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            LedgerSharingPreferences(
                statementMode = prefs[KEY_STATEMENT_MODE]?.let(::parseStatementMode) ?: LedgerStatementMode.Summary,
                defaultPeriod = prefs[KEY_DEFAULT_PERIOD]?.let(::parseDefaultPeriod) ?: LedgerSharingDefaultPeriod.Last7Sales,
                defaultDestination = prefs[KEY_DEFAULT_DESTINATION]?.let(::parseDefaultDestination)
                    ?: LedgerShareDefaultDestination.AndroidShare,
            )
        }
        .distinctUntilChanged()

    override suspend fun save(preferences: LedgerSharingPreferences) {
        dataStore.edit { prefs ->
            prefs[KEY_STATEMENT_MODE] = preferences.statementMode.name
            prefs[KEY_DEFAULT_PERIOD] = preferences.defaultPeriod.name
            prefs[KEY_DEFAULT_DESTINATION] = preferences.defaultDestination.name
        }
    }

    private fun parseStatementMode(raw: String): LedgerStatementMode =
        runCatching { LedgerStatementMode.valueOf(raw) }.getOrDefault(LedgerStatementMode.Summary)

    private fun parseDefaultPeriod(raw: String): LedgerSharingDefaultPeriod =
        runCatching { LedgerSharingDefaultPeriod.valueOf(raw) }.getOrDefault(LedgerSharingDefaultPeriod.Last7Sales)

    private fun parseDefaultDestination(raw: String): LedgerShareDefaultDestination =
        runCatching { LedgerShareDefaultDestination.valueOf(raw) }.getOrDefault(LedgerShareDefaultDestination.AndroidShare)

    companion object {
        val KEY_STATEMENT_MODE = stringPreferencesKey("ledger_sharing_statement_mode")
        val KEY_DEFAULT_PERIOD = stringPreferencesKey("ledger_sharing_default_period")
        val KEY_DEFAULT_DESTINATION = stringPreferencesKey("ledger_sharing_default_destination")
    }
}
