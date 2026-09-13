package com.jajusri.venture.feature.settings.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jajusri.venture.feature.settings.domain.model.ThemePreference
import com.jajusri.venture.feature.settings.domain.repository.ThemeObservation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings",
)

/**
 * DataStore-backed theme preference. Separate from connector_config / company_selection stores.
 */
@Singleton
class ThemePreferencesLocalDataSource @Inject constructor(
    @ApplicationContext context: Context,
) : ThemePreferencesStore {
    private val dataStore = context.appSettingsDataStore

    override val observation: Flow<ThemeObservation> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            val raw = prefs[KEY_THEME]
            when (val parsed = ThemePreference.fromStorageValue(raw)) {
                null -> ThemeObservation.Invalid(raw.orEmpty())
                else -> ThemeObservation.Available(parsed)
            }
        }
        .distinctUntilChanged()

    override suspend fun save(preference: ThemePreference) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME] = preference.toStorageValue()
        }
    }

    companion object {
        val KEY_THEME = stringPreferencesKey("theme_preference")
    }
}
