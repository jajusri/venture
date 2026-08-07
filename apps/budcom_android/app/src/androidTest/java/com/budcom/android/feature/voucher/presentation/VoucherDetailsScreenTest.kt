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
import androidx.compose.ui.test.performScrollToIndex
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
        composeRule.onNodeWithTag("voucher_details_content").performScrollToIndex(4)
        composeRule.onNodeWithTag("voucher_details_ledger_section").assertIsDisplayed()
        composeRule.onNodeWithTag("voucher_ledger_line_1").assertIsDisplayed()
        composeRule.onNodeWithTag("voucher_details_content").performScrollToIndex(5)
        composeRule.onNodeWithTag("voucher_details_metadata").assertIsDisplayed()
    }

    // Phase 3T-1: the header must clearly show the formatted voucher date alongside the number.
    @Test
    fun headerShowsFormattedVoucherDateAlongsideNumber() {
        composeRule.setContent {
            BudcomTheme {
                VoucherDetailsScreen(
                    state = VoucherDetailsUiState(
                        isInitialLoading = false,
                        details = sampleContent(dateLabel = "07 Aug 2026"),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("voucher_details_header").assertIsDisplayed()
        composeRule.onNodeWithText("07 Aug 2026").assertIsDisplayed()
    }

    @Test
    fun eligibleInvoiceShowsAllShareOptions() {
        composeRule.setContent {
            BudcomTheme {
                VoucherDetailsScreen(
                    state = VoucherDetailsUiState(
                        isInitialLoading = false,
                        details = sampleContent(),
                        canShareInvoice = true,
                        showShareOptions = true,
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("share_invoice").assertExists()
        composeRule.onNodeWithTag("share_invoice_pdf").assertExists()
        composeRule.onNodeWithTag("share_invoice_summary").assertExists()
        composeRule.onNodeWithTag("save_invoice_pdf").assertExists()
    }

    @Test
    fun unsupportedVoucherDoesNotShowShareAction() {
        composeRule.setContent {
            BudcomTheme {
                VoucherDetailsScreen(
                    state = VoucherDetailsUiState(isInitialLoading = false, details = sampleContent()),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("share_invoice").assertDoesNotExist()
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

        composeRule.onNodeWithText("Fixture item").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Qty 2 PCS  ·  @ 50.00/PCS").performScrollTo().assertIsDisplayed()
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

        composeRule.onNodeWithText("Qty 1 PCS  ·  @ 100").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Missing rate item").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Qty 1 PCS").performScrollTo().assertIsDisplayed()
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
        composeRule.onNodeWithText(longName).assertIsDisplayed()
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

    private fun sampleContent(dateLabel: String = "2026-07-27") = VoucherDetailsContentUi(
        id = "v-1",
        documentTitle = "Sales invoice",
        partyHeading = "Bill to",
        typeLabel = "Sales",
        numberLabel = "S-1",
        dateLabel = dateLabel,
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
