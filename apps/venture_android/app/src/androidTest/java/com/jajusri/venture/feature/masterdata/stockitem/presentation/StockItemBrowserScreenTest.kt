package com.jajusri.venture.feature.masterdata.stockitem.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jajusri.venture.feature.masterdata.presentation.MasterDataUiError
import com.jajusri.venture.ui.theme.VentureTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StockItemBrowserScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingState() {
        composeRule.setContent {
            VentureTheme {
                StockItemBrowserScreen(
                    state = StockItemBrowserUiState(isInitialLoading = true),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("stock_item_loading").assertIsDisplayed()
    }

    @Test
    fun emptyState() {
        composeRule.setContent {
            VentureTheme {
                StockItemBrowserScreen(
                    state = StockItemBrowserUiState(isInitialLoading = false),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("stock_item_empty").assertIsDisplayed()
    }

    @Test
    fun contentState() {
        composeRule.setContent {
            VentureTheme {
                StockItemBrowserScreen(
                    state = StockItemBrowserUiState(
                        isInitialLoading = false,
                        stockItems = listOf(
                            StockItemRowUi(
                                id = "guid:widget",
                                primaryLabel = "Widget",
                                secondaryLabel = "Primary",
                                statusLabel = "Active",
                                unitLabel = "Nos",
                                balanceLabel = "12 INR Dr",
                            ),
                        ),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("stock_item_list").assertIsDisplayed()
        composeRule.onNodeWithTag("stock_item_row_guid:widget").assertIsDisplayed()
    }

    @Test
    fun errorRetry() {
        var retried = false
        composeRule.setContent {
            VentureTheme {
                StockItemBrowserScreen(
                    state = StockItemBrowserUiState(
                        isInitialLoading = false,
                        error = MasterDataUiError.Timeout("The request timed out."),
                    ),
                    onEvent = { if (it is StockItemBrowserEvent.Retry) retried = true },
                )
            }
        }
        composeRule.onNodeWithTag("stock_item_retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun offlineBannerWithContent() {
        composeRule.setContent {
            VentureTheme {
                StockItemBrowserScreen(
                    state = StockItemBrowserUiState(
                        isInitialLoading = false,
                        isOnline = false,
                        stockItems = listOf(
                            StockItemRowUi("guid:widget", "Widget", null, "Active", null, null),
                        ),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("stock_item_offline_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("stock_item_list").assertIsDisplayed()
    }

    @Test
    fun freshnessLabelShowsWhenContentIsPresent() {
        composeRule.setContent {
            VentureTheme {
                StockItemBrowserScreen(
                    state = StockItemBrowserUiState(
                        isInitialLoading = false,
                        stockItems = listOf(
                            StockItemRowUi("guid:widget", "Widget", null, "Active", null, null),
                        ),
                        dataFreshnessAt = "2026-08-18T09:00:00Z",
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("stock_item_data_freshness").assertIsDisplayed()
    }
}
