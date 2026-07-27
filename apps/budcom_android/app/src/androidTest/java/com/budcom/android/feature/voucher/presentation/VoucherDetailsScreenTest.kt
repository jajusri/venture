package com.budcom.android.feature.voucher.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.ui.theme.BudcomTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VoucherDetailsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingState() {
        composeRule.setContent {
            BudcomTheme {
                VoucherDetailsScreen(
                    state = VoucherDetailsUiState(isInitialLoading = true),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("voucher_details_loading").assertIsDisplayed()
    }

    @Test
    fun contentState() {
        composeRule.setContent {
            BudcomTheme {
                VoucherDetailsScreen(
                    state = VoucherDetailsUiState(
                        isInitialLoading = false,
                        details = sampleContent(),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("voucher_details_content").assertIsDisplayed()
        composeRule.onNodeWithTag("voucher_details_header").assertIsDisplayed()
        composeRule.onNodeWithTag("voucher_details_metadata").assertIsDisplayed()
        composeRule.onNodeWithTag("voucher_details_ledger_section").assertIsDisplayed()
        composeRule.onNodeWithTag("voucher_ledger_line_1").assertIsDisplayed()
    }

    @Test
    fun errorRetry() {
        var retried = false
        composeRule.setContent {
            BudcomTheme {
                VoucherDetailsScreen(
                    state = VoucherDetailsUiState(
                        isInitialLoading = false,
                        error = MasterDataUiError.Timeout("The request timed out."),
                    ),
                    onEvent = { if (it is VoucherDetailsEvent.Retry) retried = true },
                )
            }
        }
        composeRule.onNodeWithTag("voucher_details_retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun offlineBannerWithContent() {
        composeRule.setContent {
            BudcomTheme {
                VoucherDetailsScreen(
                    state = VoucherDetailsUiState(
                        isInitialLoading = false,
                        isOnline = false,
                        details = sampleContent(),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("voucher_details_offline_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("voucher_details_content").assertIsDisplayed()
    }

    private fun sampleContent() = VoucherDetailsContentUi(
        id = "v-1",
        typeLabel = "Sales",
        numberLabel = "S-1",
        dateLabel = "2026-07-27",
        effectiveDateLabel = null,
        partyLabel = "Acme",
        referenceLabel = null,
        amountLabel = "100.00 (debit)",
        statusLabel = "Active",
        dataQualityLabel = "Complete",
        narration = "Hello",
        ledgerLines = listOf(
            VoucherLedgerLineUi(1, "Cash", "100.00 (debit)", "Deemed positive: yes"),
        ),
        inventoryLines = emptyList(),
    )
}
