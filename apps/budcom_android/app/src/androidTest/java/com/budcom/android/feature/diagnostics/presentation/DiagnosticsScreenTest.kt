package com.budcom.android.feature.diagnostics.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.serverconfig.domain.model.ConnectorHealth
import com.budcom.android.feature.serverconfig.domain.model.ConnectorReadiness
import com.budcom.android.ui.theme.BudcomTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DiagnosticsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsConfirmedSections() {
        composeRule.setContent {
            BudcomTheme {
                DiagnosticsScreen(state = sampleState(), onEvent = {})
            }
        }
        // diagnostics_screen's content column is verticalScroll -- sections beyond the first
        // couple are off-screen on first composition, so each must be scrolled into view before
        // asserting visibility.
        composeRule.onNodeWithTag("diagnostics_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics_application").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics_connector").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics_company").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics_sync").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics_health_status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics_ready_status").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun offlineBanner() {
        composeRule.setContent {
            BudcomTheme {
                DiagnosticsScreen(
                    state = sampleState().copy(isOnline = false),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("diagnostics_offline").assertIsDisplayed()
    }

    @Test
    fun retryOnBannerError() {
        var retried = false
        composeRule.setContent {
            BudcomTheme {
                DiagnosticsScreen(
                    state = sampleState().copy(
                        bannerError = MasterDataUiError.Timeout("The request timed out."),
                    ),
                    onEvent = { if (it is DiagnosticsEvent.Retry) retried = true },
                )
            }
        }
        composeRule.onNodeWithTag("diagnostics_retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun toolsEmitEvents() {
        val events = mutableListOf<DiagnosticsEvent>()
        composeRule.setContent {
            BudcomTheme {
                DiagnosticsScreen(
                    state = sampleState(),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeRule.onNodeWithTag("diagnostics_refresh").performClick()
        composeRule.onNodeWithTag("diagnostics_recheck_health").performClick()
        composeRule.onNodeWithTag("diagnostics_recheck_readiness").performClick()
        composeRule.onNodeWithTag("diagnostics_open_server_config").performClick()
        assertTrue(events.contains(DiagnosticsEvent.Refresh))
        assertTrue(events.contains(DiagnosticsEvent.RecheckHealth))
        assertTrue(events.contains(DiagnosticsEvent.RecheckReadiness))
        assertTrue(events.contains(DiagnosticsEvent.OpenServerConfig))
    }

    private fun sampleState() = DiagnosticsUiState(
        isInitialLoading = false,
        application = ApplicationSectionUi(
            appName = "BudCom",
            versionName = "1.0",
            versionCode = 1,
            buildTypeLabel = "Debug",
        ),
        connector = ConnectorSectionUi(
            baseUrl = "http://10.0.2.2:8080/",
            health = ConnectorHealth(
                status = "ok",
                schemaVersion = "1.0.0",
                connectorVersion = "0.1.0",
                tallyReachable = true,
                readOnly = true,
                bindHost = "0.0.0.0",
                bindPort = 8080,
                networkExposure = "loopback",
                networkExposureWarning = null,
                networkPolicySatisfied = true,
                authenticatedLanAccessEnabled = false,
                services = emptyList(),
                startupCorrelationId = null,
                repositoryAvailable = true,
                databaseAccessible = true,
            ),
            readiness = ConnectorReadiness(
                status = "ready",
                repositoryAvailable = true,
                databaseAccessible = true,
                voucherSynchronizationComposed = false,
                voucherApplicationComposed = true,
                httpStatus = 200,
            ),
            connectionState = "connected",
            connectionHostPort = "127.0.0.1:9000",
            connectionCircuit = "closed",
            connectionSafeMode = false,
        ),
        company = CompanySectionUi(
            companyId = "c1",
            companyName = "Company",
            sessionLabel = "Valid",
        ),
        sync = SyncSectionUi(
            activeLabel = "Idle",
            lastSuccess = "2026-07-27T00:00:00.000Z",
            lastFailure = null,
        ),
        searchNote = "Search note",
        masterDataNote = "Master note",
        voucherNote = "Voucher note",
    )
}
