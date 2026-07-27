package com.budcom.android.feature.settings.domain.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.settings.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow

/**
 * Persisted theme preference. Compose must not access DataStore directly.
 */
interface ThemePreferencesRepository {
    /**
     * Emits the current preference. Invalid stored values emit [ThemeObservation.Invalid].
     */
    fun observeTheme(): Flow<ThemeObservation>

    suspend fun setTheme(preference: ThemePreference): AppResult<Unit>
}

sealed interface ThemeObservation {
    data class Available(val preference: ThemePreference) : ThemeObservation
    data class Invalid(val rawValue: String) : ThemeObservation
}
