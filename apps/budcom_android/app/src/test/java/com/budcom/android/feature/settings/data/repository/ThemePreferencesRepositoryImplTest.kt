package com.budcom.android.feature.settings.data.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.settings.data.local.ThemePreferencesStore
import com.budcom.android.feature.settings.domain.model.ThemePreference
import com.budcom.android.feature.settings.domain.repository.ThemeObservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferencesRepositoryImplTest {

    @Test
    fun setThemePersistsAndIsObserved() = runTest {
        val store = InMemoryThemeStore()
        val repository = ThemePreferencesRepositoryImpl(store)
        assertEquals(
            ThemeObservation.Available(ThemePreference.System),
            repository.observeTheme().first(),
        )
        val result = repository.setTheme(ThemePreference.Dark)
        assertTrue(result is AppResult.Success)
        assertEquals(
            ThemeObservation.Available(ThemePreference.Dark),
            repository.observeTheme().first(),
        )
        assertEquals(ThemePreference.STORAGE_DARK, store.rawValue)
    }

    @Test
    fun invalidStoredValueSurfacesAsInvalidObservation() = runTest {
        val store = InMemoryThemeStore(initialRaw = "sepia")
        val repository = ThemePreferencesRepositoryImpl(store)
        assertEquals(
            ThemeObservation.Invalid("sepia"),
            repository.observeTheme().first(),
        )
    }

    @Test
    fun lightAndSystemPersistIndependently() = runTest {
        val store = InMemoryThemeStore()
        val repository = ThemePreferencesRepositoryImpl(store)
        repository.setTheme(ThemePreference.Light)
        assertEquals(ThemeObservation.Available(ThemePreference.Light), repository.observeTheme().first())
        repository.setTheme(ThemePreference.System)
        assertEquals(ThemeObservation.Available(ThemePreference.System), repository.observeTheme().first())
    }
}

private class InMemoryThemeStore(
    initialRaw: String? = null,
) : ThemePreferencesStore {
    var rawValue: String? = initialRaw
        private set

    private val flow = MutableStateFlow(observeFromRaw(rawValue))

    override val observation: Flow<ThemeObservation> = flow

    override suspend fun save(preference: ThemePreference) {
        rawValue = preference.toStorageValue()
        flow.value = ThemeObservation.Available(preference)
    }

    private fun observeFromRaw(raw: String?): ThemeObservation {
        val parsed = ThemePreference.fromStorageValue(raw)
        return if (parsed == null) {
            ThemeObservation.Invalid(raw.orEmpty())
        } else {
            ThemeObservation.Available(parsed)
        }
    }
}
