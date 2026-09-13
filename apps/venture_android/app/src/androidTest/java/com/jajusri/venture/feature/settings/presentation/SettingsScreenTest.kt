package com.jajusri.venture.feature.settings.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jajusri.venture.feature.settings.domain.model.ApplicationInformation
import com.jajusri.venture.feature.settings.domain.model.ThemePreference
import com.jajusri.venture.navigation.StartupRoutingState
import com.jajusri.venture.ui.theme.VentureTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsImplementedSectionsOnly() {
        composeRule.setContent {
            VentureTheme {
                SettingsScreen(state = sampleState(), onEvent = {})
            }
        }
        // settings_screen's content is a scrollable column -- sections beyond the first couple
        // are off-screen on first composition.
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_connection").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_appearance").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_company").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_sync").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_diagnostics").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_about").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun themeSelectionEmitsEvent() {
        val events = mutableListOf<SettingsEvent>()
        composeRule.setContent {
            VentureTheme {
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
            VentureTheme {
                SettingsScreen(state = sampleState(), onEvent = { events.add(it) })
            }
        }
        // These navigation rows are stacked in a scrollable Settings list; a click on an
        // off-screen row can silently miss rather than throw, so each must be scrolled into view.
        composeRule.onNodeWithTag("settings_open_server_config").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_open_company").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_open_sync").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_open_diagnostics").performScrollTo().performClick()
        assertTrue(events.contains(SettingsEvent.OpenServerConfig))
        assertTrue(events.contains(SettingsEvent.OpenCompanySelection))
        assertTrue(events.contains(SettingsEvent.OpenSync))
        assertTrue(events.contains(SettingsEvent.OpenDiagnostics))
    }

    // 24. LegacyEligible exposes one secure-migration action
    @Test
    fun legacyEligibleShowsSecureThisConnectionAction() {
        val events = mutableListOf<SettingsEvent>()
        composeRule.setContent {
            VentureTheme {
                SettingsScreen(
                    state = sampleState().copy(secureConnectionState = StartupRoutingState.LegacyEligible),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeRule.onNodeWithTag("settings_secure_this_connection").assertIsDisplayed().performClick()
        assertTrue(events.contains(SettingsEvent.OpenSecurePairing))
    }

    // ACTIVE trust remains explicitly manageable without exposing the legacy/manual URL editor.
    @Test
    fun secureActiveShowsManagePairingAndHidesLegacyServerConfig() {
        val events = mutableListOf<SettingsEvent>()
        composeRule.setContent {
            VentureTheme {
                SettingsScreen(
                    state = sampleState().copy(secureConnectionState = StartupRoutingState.SecureActive),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeRule.onAllNodesWithTag("settings_secure_this_connection").assertCountEquals(0)
        composeRule.onAllNodesWithTag("settings_re_pair").assertCountEquals(0)
        composeRule.onAllNodesWithTag("settings_open_server_config").assertCountEquals(0)
        composeRule.onNodeWithTag("settings_manage_secure_pairing").assertIsDisplayed().performClick()
        assertTrue(events.contains(SettingsEvent.OpenSecurePairing))
    }

    // 27. migration action is absent for RePairRequired — a distinct re-pair action is shown instead
    @Test
    fun rePairRequiredShowsRePairActionNotMigrationAction() {
        val events = mutableListOf<SettingsEvent>()
        composeRule.setContent {
            VentureTheme {
                SettingsScreen(
                    state = sampleState().copy(secureConnectionState = StartupRoutingState.RePairRequired),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeRule.onNodeWithTag("settings_re_pair").assertIsDisplayed().performClick()
        assertTrue(events.contains(SettingsEvent.OpenSecurePairing))
        composeRule.onAllNodesWithTag("settings_secure_this_connection").assertCountEquals(0)
    }

    @Test
    fun offlineBanner() {
        composeRule.setContent {
            VentureTheme {
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
        secureConnectionState = StartupRoutingState.LegacyEligible,
        application = ApplicationInformation(
            appName = "Venture",
            packageName = "com.jajusri.venture.debug",
            versionName = "0.1.0",
            versionCode = 1,
            buildTypeLabel = "Debug",
        ),
    )
}
