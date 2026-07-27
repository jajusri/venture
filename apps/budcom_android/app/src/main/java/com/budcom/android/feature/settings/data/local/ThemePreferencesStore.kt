package com.budcom.android.feature.settings.data.local

import com.budcom.android.feature.settings.domain.model.ThemePreference
import com.budcom.android.feature.settings.domain.repository.ThemeObservation
import kotlinx.coroutines.flow.Flow

/**
 * Local persistence port for theme preference (DataStore-backed in production).
 */
interface ThemePreferencesStore {
    val observation: Flow<ThemeObservation>
    suspend fun save(preference: ThemePreference)
}
