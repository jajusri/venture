package com.budcom.android.feature.settings.domain.model

/**
 * Persisted appearance preference. Default is [System].
 */
enum class ThemePreference {
    System,
    Light,
    Dark,
    ;

    fun toStorageValue(): String = when (this) {
        System -> STORAGE_SYSTEM
        Light -> STORAGE_LIGHT
        Dark -> STORAGE_DARK
    }

    companion object {
        const val STORAGE_SYSTEM = "system"
        const val STORAGE_LIGHT = "light"
        const val STORAGE_DARK = "dark"

        /**
         * Parses a DataStore value. Returns `null` when the value is present but unrecognized.
         * Missing (`null`) means the default [System] preference.
         */
        fun fromStorageValue(raw: String?): ThemePreference? = when (raw) {
            null -> System
            STORAGE_SYSTEM -> System
            STORAGE_LIGHT -> Light
            STORAGE_DARK -> Dark
            else -> null
        }
    }
}

/**
 * Confirmed application metadata from Android BuildConfig / package manager.
 * Does not invent build date or Connector build ids.
 */
data class ApplicationInformation(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val buildTypeLabel: String,
)

/**
 * Aggregated settings snapshot for presentation.
 */
data class SettingsSnapshot(
    val themePreference: ThemePreference,
    val themeConfigurationError: String? = null,
    val baseUrl: String,
    val isOnline: Boolean,
    val companyId: String?,
    val companyName: String?,
    val syncStatusLabel: String,
    val connectorVersion: String?,
    val application: ApplicationInformation,
)
