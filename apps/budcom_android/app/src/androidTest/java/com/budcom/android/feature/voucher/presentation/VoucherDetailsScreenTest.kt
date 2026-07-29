package com.budcom.android.feature.voucher.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    @Test
    fun inventoryLineShowsExactRateWithUnitSuffix() {
        showInventory(
            listOf(inventoryLine(1, "Fixture item", "2 PCS", "50.00/PCS", "100.00")),
        )

        composeRule.onNodeWithText("1. Fixture item").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Quantity: 2 PCS").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Rate: 50.00/PCS").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("100.00").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun inventoryLineShowsNormalRateAndOmitsMissingRate() {
        showInventory(
            listOf(
                inventoryLine(1, "Plain rate item", "1 PCS", "100", "100"),
                inventoryLine(2, "Missing rate item", "1 PCS", null, "25"),
            ),
        )

        composeRule.onNodeWithText("Rate: 100").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2. Missing rate item").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Rate: ").assertCountEquals(0)
    }

    @Test
    fun multipleInventoryLinesKeepLongItemNamesVisible() {
        val longName = "Extra long controlled inventory item name that must remain visible without truncating its semantic text"
        showInventory(
            listOf(
                inventoryLine(1, "First item", "1 PCS", "10/PCS", "10"),
                inventoryLine(2, longName, "3 PCS", "20/PCS", "60"),
                inventoryLine(3, "Third item", "4 PCS", "5/PCS", "20"),
            ),
        )

        composeRule.onNodeWithTag("voucher_inventory_line_1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("voucher_inventory_line_2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2. $longName").assertIsDisplayed()
        composeRule.onNodeWithTag("voucher_inventory_line_3").performScrollTo().assertIsDisplayed()
    }

    private fun showInventory(lines: List<VoucherInventoryLineUi>) {
        composeRule.setContent {
            BudcomTheme {
                VoucherDetailsScreen(
                    state = VoucherDetailsUiState(
                        isInitialLoading = false,
                        details = sampleContent().copy(inventoryLines = lines),
                    ),
                    onEvent = {},
                )
            }
        }
    }

    private fun inventoryLine(
        number: Int,
        name: String,
        quantity: String?,
        rate: String?,
        amount: String?,
    ) = VoucherInventoryLineUi(
        lineNumber = number,
        itemName = name,
        quantityLabel = quantity,
        rateLabel = rate,
        amountLabel = amount,
    )

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
