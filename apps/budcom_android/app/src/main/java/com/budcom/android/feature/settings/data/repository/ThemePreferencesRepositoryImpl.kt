package com.budcom.android.feature.settings.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.settings.data.local.ThemePreferencesStore
import com.budcom.android.feature.settings.domain.model.ThemePreference
import com.budcom.android.feature.settings.domain.repository.ThemeObservation
import com.budcom.android.feature.settings.domain.repository.ThemePreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemePreferencesRepositoryImpl @Inject constructor(
    private val store: ThemePreferencesStore,
) : ThemePreferencesRepository {
    override fun observeTheme(): Flow<ThemeObservation> = store.observation

    override suspend fun setTheme(preference: ThemePreference): AppResult<Unit> =
        try {
            store.save(preference)
            AppResult.Success(Unit)
        } catch (error: Exception) {
            AppResult.Failure(AppError.Unexpected(error))
        }
}
