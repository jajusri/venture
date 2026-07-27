package com.budcom.android.feature.settings.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.budcom.android.feature.settings.domain.model.ApplicationInformation
import com.budcom.android.feature.settings.domain.model.ThemePreference
import com.budcom.android.ui.theme.BudcomTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsImplementedSectionsOnly() {
        composeRule.setContent {
            BudcomTheme {
                SettingsScreen(state = sampleState(), onEvent = {})
            }
        }
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_connection").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_appearance").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_company").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_sync").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_diagnostics").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_about").assertIsDisplayed()
    }

    @Test
    fun themeSelectionEmitsEvent() {
        val events = mutableListOf<SettingsEvent>()
        composeRule.setContent {
            BudcomTheme {
                SettingsScreen(state = sampleState(), onEvent = { events.add(it) })
            }
        }
        composeRule.onNodeWithTag("settings_theme_dark").performClick()
        assertTrue(events.any { it is SettingsEvent.SelectTheme && it.preference == ThemePreference.Dark })
    }

    @Test
    fun navigationActionsEmitEvents() {
        val events = mutableListOf<SettingsEvent>()
        composeRule.setContent {
            BudcomTheme {
                SettingsScreen(state = sampleState(), onEvent = { events.add(it) })
            }
        }
        composeRule.onNodeWithTag("settings_open_server_config").performClick()
        composeRule.onNodeWithTag("settings_open_company").performClick()
        composeRule.onNodeWithTag("settings_open_sync").performClick()
        composeRule.onNodeWithTag("settings_open_diagnostics").performClick()
        assertTrue(events.contains(SettingsEvent.OpenServerConfig))
        assertTrue(events.contains(SettingsEvent.OpenCompanySelection))
        assertTrue(events.contains(SettingsEvent.OpenSync))
        assertTrue(events.contains(SettingsEvent.OpenDiagnostics))
    }

    @Test
    fun offlineBanner() {
        composeRule.setContent {
            BudcomTheme {
                SettingsScreen(
                    state = sampleState().copy(isOnline = false),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("settings_offline").assertIsDisplayed()
    }

    private fun sampleState() = SettingsUiState(
        isInitialLoading = false,
        baseUrl = "http://10.0.2.2:8080/",
        themePreference = ThemePreference.System,
        companyId = "c1",
        companyName = "Company",
        syncStatusLabel = "Never synced",
        connectorVersion = "0.1.0",
        application = ApplicationInformation(
            appName = "BudCom",
            packageName = "com.budcom.android.debug",
            versionName = "0.1.0",
            versionCode = 1,
            buildTypeLabel = "Debug",
        ),
    )
}
