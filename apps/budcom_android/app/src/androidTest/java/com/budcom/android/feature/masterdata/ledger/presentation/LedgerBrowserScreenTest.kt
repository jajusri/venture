package com.budcom.android.feature.masterdata.ledger.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.ui.theme.BudcomTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LedgerBrowserScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingState() {
        composeRule.setContent {
            BudcomTheme {
                LedgerBrowserScreen(
                    state = LedgerBrowserUiState(isInitialLoading = true),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("ledger_loading").assertIsDisplayed()
    }

    @Test
    fun emptyState() {
        composeRule.setContent {
            BudcomTheme {
                LedgerBrowserScreen(
                    state = LedgerBrowserUiState(isInitialLoading = false),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("ledger_empty").assertIsDisplayed()
    }

    @Test
    fun contentState() {
        composeRule.setContent {
            BudcomTheme {
                LedgerBrowserScreen(
                    state = LedgerBrowserUiState(
                        isInitialLoading = false,
                        ledgers = listOf(
                            LedgerRowUi("guid:cash", "Cash", "Cash-in-Hand", "Active", "10 INR Dr"),
                        ),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("ledger_list").assertIsDisplayed()
        composeRule.onNodeWithTag("ledger_row_guid:cash").assertIsDisplayed()
    }

    @Test
    fun errorRetry() {
        var retried = false
        composeRule.setContent {
            BudcomTheme {
                LedgerBrowserScreen(
                    state = LedgerBrowserUiState(
                        isInitialLoading = false,
                        error = MasterDataUiError.Timeout("The request timed out."),
                    ),
                    onEvent = { if (it is LedgerBrowserEvent.Retry) retried = true },
                )
            }
        }
        composeRule.onNodeWithTag("ledger_retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun freshnessLabelShowsWhenContentIsPresent() {
        composeRule.setContent {
            BudcomTheme {
                LedgerBrowserScreen(
                    state = LedgerBrowserUiState(
                        isInitialLoading = false,
                        ledgers = listOf(LedgerRowUi("guid:cash", "Cash", null, "Active", null)),
                        dataFreshnessAt = "2026-08-18T09:00:00Z",
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("ledger_data_freshness").assertIsDisplayed()
        composeRule.onNodeWithText("Data last synced: 2026-08-18T09:00:00Z").assertIsDisplayed()
    }

    @Test
    fun freshnessLabelIsAbsentWithoutContent() {
        composeRule.setContent {
            BudcomTheme {
                LedgerBrowserScreen(
                    state = LedgerBrowserUiState(isInitialLoading = false),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("ledger_data_freshness").assertDoesNotExist()
    }

    @Test
    fun offlineBannerWithContent() {
        composeRule.setContent {
            BudcomTheme {
                LedgerBrowserScreen(
                    state = LedgerBrowserUiState(
                        isInitialLoading = false,
                        isOnline = false,
                        ledgers = listOf(
                            LedgerRowUi("guid:cash", "Cash", null, "Active", null),
                        ),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("ledger_offline_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("ledger_list").assertIsDisplayed()
    }
}
