package com.jajusri.venture.feature.settings.data.repository

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.settings.data.local.ThemePreferencesStore
import com.jajusri.venture.feature.settings.domain.model.ThemePreference
import com.jajusri.venture.feature.settings.domain.repository.ThemeObservation
import com.jajusri.venture.feature.settings.domain.repository.ThemePreferencesRepository
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
