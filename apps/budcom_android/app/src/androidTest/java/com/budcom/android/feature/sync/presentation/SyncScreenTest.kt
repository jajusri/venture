package com.budcom.android.feature.sync.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.sync.domain.model.SyncTarget
import com.budcom.android.ui.theme.BudcomTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SyncScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun idleShowsTargetsAndNoAutoBusy() {
        composeRule.setContent {
            BudcomTheme {
                SyncScreen(state = SyncUiState(companyId = "c1"), onEvent = {})
            }
        }
        // sync_screen's content is a scrollable column; each target card is tall enough that
        // later cards sit below the initially-visible area.
        //
        // defaultCards() (SyncUiState.kt) marks Vouchers `available = true`, so TargetCard's
        // "${tag}_unavailable" branch never renders for this state -- only the interactive
        // "${tag}_start" Button does. "sync_target_vouchers_unavailable" was a stale assumption
        // that never matched this fixture; asserting on the card itself (matching the other two
        // lines) verifies what this test actually intends: all three target cards render in idle.
        composeRule.onNodeWithTag("sync_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_target_ledgers").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sync_target_stockitems").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sync_target_vouchers").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sync_run_available").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noCompanyShowsSelectAction() {
        var opened = false
        composeRule.setContent {
            BudcomTheme {
                SyncScreen(
                    state = SyncUiState(companyId = null),
                    onEvent = { if (it is SyncEvent.OpenCompanySelection) opened = true },
                )
            }
        }
        composeRule.onNodeWithTag("sync_open_company").performClick()
        assertTrue(opened)
    }

    @Test
    fun offlineBanner() {
        composeRule.setContent {
            BudcomTheme {
                SyncScreen(
                    state = SyncUiState(companyId = "c1", isOnline = false),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("sync_offline").assertIsDisplayed()
    }

    @Test
    fun indeterminateProgressWhenBusyWithoutFraction() {
        composeRule.setContent {
            BudcomTheme {
                SyncScreen(
                    state = SyncUiState(
                        companyId = "c1",
                        isBusy = true,
                        phase = SyncPhase.Running,
                        activeTarget = SyncTarget.Ledgers,
                        activeProgress = SyncProgressUi(
                            statusLabel = "Running",
                            processedLabel = "Processed 1",
                            determinateFraction = null,
                            lastError = null,
                            cancelRequested = false,
                        ),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("sync_active_run").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_progress_indeterminate").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_progress_indeterminate_hint").assertIsDisplayed()
    }

    @Test
    fun errorRetry() {
        var retried = false
        composeRule.setContent {
            BudcomTheme {
                SyncScreen(
                    state = SyncUiState(
                        companyId = "c1",
                        bannerError = MasterDataUiError.Timeout("The request timed out."),
                    ),
                    onEvent = { if (it is SyncEvent.Retry) retried = true },
                )
            }
        }
        composeRule.onNodeWithTag("sync_retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun disabledStartWhenCannotSync() {
        composeRule.setContent {
            BudcomTheme {
                SyncScreen(
                    state = SyncUiState(
                        companyId = "c1",
                        targets = listOf(
                            SyncTargetCardUi(
                                target = SyncTarget.Ledgers,
                                title = "Ledgers",
                                available = true,
                                statusLine = "Idle",
                                lastSyncedLine = null,
                                canSync = false,
                                canCancel = false,
                            ),
                        ),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("sync_target_ledgers_start").assertIsNotEnabled()
    }
}
