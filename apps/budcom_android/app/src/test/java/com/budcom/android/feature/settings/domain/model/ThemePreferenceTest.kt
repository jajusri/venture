package com.budcom.android.feature.settings.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemePreferenceTest {
    @Test
    fun roundTripsKnownValues() {
        ThemePreference.entries.forEach { preference ->
            assertEquals(preference, ThemePreference.fromStorageValue(preference.toStorageValue()))
        }
    }

    @Test
    fun missingDefaultsToSystem() {
        assertEquals(ThemePreference.System, ThemePreference.fromStorageValue(null))
    }

    @Test
    fun unrecognizedReturnsNull() {
        assertNull(ThemePreference.fromStorageValue("sepia"))
        assertNull(ThemePreference.fromStorageValue(""))
    }
}
