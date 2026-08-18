package com.budcom.android.feature.dashboard.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.budcom.android.feature.dashboard.domain.model.DashboardOperationalMode
import com.budcom.android.feature.dashboard.domain.model.DashboardSessionValidity
import com.budcom.android.ui.theme.BudcomTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fullyOperationalDashboardIsShown() {
        composeRule.setContent {
            BudcomTheme {
                DashboardScreen(state = operationalState(), onEvent = {})
            }
        }
        composeRule.onNodeWithTag("dashboard_secondary_actions").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_content").assertIsDisplayed()
    }

    @Test
    fun offlineBannerIsShown() {
        composeRule.setContent {
            BudcomTheme {
                DashboardScreen(
                    state = operationalState().copy(
                        isOnline = false,
                        operationalMode = DashboardOperationalMode.Offline,
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("dashboard_banner_offline").assertIsDisplayed()
    }

    @Test
    fun noCompanySelectedStateIsShown() {
        composeRule.setContent {
            BudcomTheme {
                DashboardScreen(
                    state = operationalState().copy(
                        selectedCompanyId = null,
                        selectedCompanyName = null,
                        sessionValidity = DashboardSessionValidity.NoCompany,
                        operationalMode = DashboardOperationalMode.NoCompanySelected,
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("dashboard_banner_no_company").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_company_value").assertIsDisplayed()
    }

    @Test
    fun notReadyStateIsShown() {
        composeRule.setContent {
            BudcomTheme {
                DashboardScreen(
                    state = operationalState().copy(
                        readinessLabel = ReadinessLabel.NotReady,
                        operationalMode = DashboardOperationalMode.NotReady,
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("dashboard_banner_not_ready").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_readiness_status").assertIsDisplayed()
    }

    @Test
    fun navigationActionsEmitEvents() {
        val events = mutableListOf<DashboardEvent>()
        composeRule.setContent {
            BudcomTheme {
                DashboardScreen(
                    state = operationalState(),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeRule.onNodeWithTag("dashboard_open_company").performClick()
        composeRule.onNodeWithTag("dashboard_open_master_data").performClick()
        composeRule.onNodeWithTag("dashboard_primary_vouchers").performClick()
        composeRule.onNodeWithTag("dashboard_search_entry").performClick()
        composeRule.onNodeWithTag("dashboard_open_sync").performClick()
        composeRule.onNodeWithTag("dashboard_open_diagnostics").performClick()
        composeRule.onNodeWithTag("dashboard_topbar_settings").performClick()
        assertTrue(events.contains(DashboardEvent.OpenMasterData))
        assertTrue(events.contains(DashboardEvent.OpenVouchers))
        assertTrue(events.contains(DashboardEvent.OpenSearch))
        assertTrue(events.contains(DashboardEvent.OpenSync))
        assertTrue(events.contains(DashboardEvent.OpenDiagnostics))
        assertTrue(events.contains(DashboardEvent.OpenSettings))
        assertTrue(events.contains(DashboardEvent.OpenCompanySelection))
    }

    @Test
    fun homeLayoutElementsAreShownAndNavigable() {
        val events = mutableListOf<DashboardEvent>()
        composeRule.setContent {
            BudcomTheme {
                DashboardScreen(
                    state = operationalState(),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeRule.onNodeWithTag("dashboard_search_entry").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_compact_status").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_primary_vouchers").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_primary_ledgers").assertIsDisplayed()

        composeRule.onNodeWithTag("dashboard_search_entry").performClick()
        composeRule.onNodeWithTag("dashboard_primary_vouchers").performClick()
        composeRule.onNodeWithTag("dashboard_primary_ledgers").performClick()
        composeRule.onNodeWithTag("dashboard_compact_sync").performClick()
        composeRule.onNodeWithTag("dashboard_topbar_sync").assertIsEnabled().performClick()

        assertTrue(events.contains(DashboardEvent.OpenSearch))
        assertTrue(events.contains(DashboardEvent.OpenVouchers))
        assertTrue(events.contains(DashboardEvent.OpenLedgers))
        assertTrue(events.count { it == DashboardEvent.Refresh } >= 2)
    }

    @Test
    fun dincharyaPrimaryEntryIsShownAndNavigable() {
        val events = mutableListOf<DashboardEvent>()
        composeRule.setContent {
            BudcomTheme {
                DashboardScreen(
                    state = operationalState(),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeRule.onNodeWithTag("dashboard_primary_dincharya").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_primary_dincharya").performClick()
        assertTrue(events.contains(DashboardEvent.OpenDincharya))
    }

    @Test
    fun refreshAndTestConnectionActionsEmitEvents() {
        val events = mutableListOf<DashboardEvent>()
        composeRule.setContent {
            BudcomTheme {
                DashboardScreen(
                    state = operationalState().copy(
                        readinessLabel = ReadinessLabel.NotReady,
                        operationalMode = DashboardOperationalMode.NotReady,
                    ),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeRule.onNodeWithTag("dashboard_test_connection").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("dashboard_validate_session").assertIsEnabled().performClick()
        assertTrue(events.contains(DashboardEvent.TestConnection))
        assertTrue(events.contains(DashboardEvent.ValidateSession))
    }

    @Test
    fun refreshingKeepsExistingContent() {
        composeRule.setContent {
            BudcomTheme {
                DashboardScreen(
                    state = operationalState().copy(isRefreshing = true),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("dashboard_content").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_busy_hint").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_secondary_actions").assertIsDisplayed()
    }

    @Test
    fun initialLoadingWithoutContentShowsSpinner() {
        composeRule.setContent {
            BudcomTheme {
                DashboardScreen(
                    state = DashboardUiState(isInitialLoading = true),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("dashboard_loading").assertIsDisplayed()
    }

    private fun operationalState() = DashboardUiState(
        isInitialLoading = false,
        isOnline = true,
        baseUrl = "http://10.0.2.2:8080/",
        connectorConnected = true,
        readinessLabel = ReadinessLabel.Ready,
        lastSuccessfulHealthCheckEpochMillis = 1_700_000_000_000L,
        selectedCompanyId = "estimation",
        selectedCompanyName = "ESTIMATION",
        sessionValidity = DashboardSessionValidity.Valid,
        lastSuccessfulSessionValidationEpochMillis = 1_700_000_000_000L,
        operationalMode = DashboardOperationalMode.FullyOperational,
    )
}
