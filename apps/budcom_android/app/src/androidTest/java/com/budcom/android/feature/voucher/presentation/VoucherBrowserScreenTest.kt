package com.budcom.android.feature.voucher.presentation

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

class VoucherBrowserScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingState() {
        composeRule.setContent {
            BudcomTheme {
                VoucherBrowserScreen(
                    state = VoucherBrowserUiState(isInitialLoading = true),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("voucher_loading").assertIsDisplayed()
    }

    @Test
    fun emptyState() {
        composeRule.setContent {
            BudcomTheme {
                VoucherBrowserScreen(
                    state = VoucherBrowserUiState(isInitialLoading = false),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("voucher_empty").assertIsDisplayed()
    }

    @Test
    fun contentState() {
        var openedId: String? = null
        composeRule.setContent {
            BudcomTheme {
                VoucherBrowserScreen(
                    state = VoucherBrowserUiState(
                        isInitialLoading = false,
                        vouchers = listOf(
                            VoucherRowUi(
                                id = "v-1",
                                primaryLabel = "S-1",
                                secondaryLabel = "Acme",
                                dateLabel = "2026-07-27",
                                typeLabel = "Sales",
                                statusLabel = "Active",
                                amountLabel = "100.00 (debit)",
                            ),
                        ),
                    ),
                    onEvent = {},
                    onOpenVoucherDetails = { openedId = it },
                )
            }
        }
        composeRule.onNodeWithTag("voucher_list").assertIsDisplayed()
        composeRule.onNodeWithTag("voucher_row_v-1").assertIsDisplayed().performClick()
        assertTrue(openedId == "v-1")
    }

    // Phase 3T-1, updated for the LEFT type/number · CENTER party · RIGHT date row layout: a long
    // party name must not hide the date column, and the row must still render the formatted date
    // without crashing — CENTER absorbs the ellipsis, RIGHT (date) is never weighted/truncated.
    @Test
    fun rowWithLongPartyNameStillShowsNumberAndDate() {
        composeRule.setContent {
            BudcomTheme {
                VoucherBrowserScreen(
                    state = VoucherBrowserUiState(
                        isInitialLoading = false,
                        vouchers = listOf(
                            VoucherRowUi(
                                id = "v-1",
                                primaryLabel = "S-1",
                                secondaryLabel = null,
                                dateLabel = "07 Aug 2026",
                                typeLabel = "Sales",
                                statusLabel = "Active",
                                amountLabel = null,
                                partyName = "A Very Long Synthetic Test Trading Company Name Private Limited",
                            ),
                        ),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("voucher_row_v-1").assertIsDisplayed()
        composeRule.onNodeWithText("Sales · S-1").assertIsDisplayed()
        composeRule.onNodeWithText("07 Aug 2026").assertIsDisplayed()
    }

    @Test
    fun errorRetry() {
        var retried = false
        composeRule.setContent {
            BudcomTheme {
                VoucherBrowserScreen(
                    state = VoucherBrowserUiState(
                        isInitialLoading = false,
                        error = MasterDataUiError.Timeout("The request timed out."),
                    ),
                    onEvent = { if (it is VoucherBrowserEvent.Retry) retried = true },
                )
            }
        }
        composeRule.onNodeWithTag("voucher_retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun offlineBannerWithContent() {
        composeRule.setContent {
            BudcomTheme {
                VoucherBrowserScreen(
                    state = VoucherBrowserUiState(
                        isInitialLoading = false,
                        isOnline = false,
                        vouchers = listOf(
                            VoucherRowUi("v-1", "S-1", null, "2026-07-27", "Sales", "Active", null),
                        ),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("voucher_offline_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("voucher_list").assertIsDisplayed()
    }
}
