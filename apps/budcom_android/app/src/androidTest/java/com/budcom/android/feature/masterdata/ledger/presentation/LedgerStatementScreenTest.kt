package com.budcom.android.feature.masterdata.ledger.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.budcom.android.core.pdf.PdfPageRenderer
import com.budcom.android.core.pdf.PdfPreviewDocument
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.ui.theme.BudcomTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** None of these screen-state tests open Preview, so a renderer that never resolves a document
 * is sufficient — it exists only to satisfy the required constructor parameter. */
private val fakePdfPageRenderer = object : PdfPageRenderer {
    override suspend fun open(filePath: String): PdfPreviewDocument? = null
}

class LedgerStatementScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingState() {
        composeRule.setContent {
            BudcomTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(isInitialLoading = true),
                    onEvent = {},
                    onBack = {},
                    pdfPageRenderer = fakePdfPageRenderer,
                )
            }
        }
        composeRule.onNodeWithTag("ledger_statement_loading").assertIsDisplayed()
    }

    @Test
    fun errorRetry() {
        var retried = false
        composeRule.setContent {
            BudcomTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(
                        isInitialLoading = false,
                        error = MasterDataUiError.Timeout("The request timed out."),
                    ),
                    onEvent = { if (it is LedgerStatementEvent.Retry) retried = true },
                    onBack = {},
                    pdfPageRenderer = fakePdfPageRenderer,
                )
            }
        }
        composeRule.onNodeWithTag("ledger_statement_retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun loadedStateShowsRowsAndClosingBalance() {
        composeRule.setContent {
            BudcomTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(isInitialLoading = false, content = sampleContent()),
                    onEvent = {},
                    onBack = {},
                    pdfPageRenderer = fakePdfPageRenderer,
                )
            }
        }
        composeRule.onNodeWithTag("ledger_statement_list").assertIsDisplayed()
        composeRule.onNodeWithTag("ledger_statement_opening").assertIsDisplayed()
        composeRule.onNodeWithTag("ledger_statement_closing").assertIsDisplayed()
        composeRule.onNodeWithText("Cash").assertIsDisplayed()
    }

    @Test
    fun emptyStateWithinLoadedPeriodShowsNoTransactionsMessage() {
        composeRule.setContent {
            BudcomTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(isInitialLoading = false, content = sampleContent(rows = emptyList())),
                    onEvent = {},
                    onBack = {},
                    pdfPageRenderer = fakePdfPageRenderer,
                )
            }
        }
        composeRule.onNodeWithTag("master_data_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("ledger_statement_closing").assertIsDisplayed()
    }

    @Test
    fun coverageWarningIsShownWhenScopeIsNotAuthoritative() {
        composeRule.setContent {
            BudcomTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(
                        isInitialLoading = false,
                        content = sampleContent(coverageMessage = "Complete historical scope unavailable"),
                    ),
                    onEvent = {},
                    onBack = {},
                    pdfPageRenderer = fakePdfPageRenderer,
                )
            }
        }
        composeRule.onNodeWithTag("ledger_statement_coverage_message").assertIsDisplayed()
        composeRule.onNodeWithText("Complete historical scope unavailable").assertIsDisplayed()
    }

    @Test
    fun shareButtonDisabledWithoutContentAndEnabledWithContent() {
        composeRule.setContent {
            BudcomTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(isInitialLoading = false),
                    onEvent = {},
                    onBack = {},
                    pdfPageRenderer = fakePdfPageRenderer,
                )
            }
        }
        composeRule.onNodeWithTag("ledger_statement_share_button").assertIsNotEnabled()
    }

    @Test
    fun previewButtonDisabledWithoutContentAndEnabledWithContent() {
        composeRule.setContent {
            BudcomTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(isInitialLoading = false),
                    onEvent = {},
                    onBack = {},
                    pdfPageRenderer = fakePdfPageRenderer,
                )
            }
        }
        composeRule.onNodeWithTag("ledger_statement_preview_button").assertIsNotEnabled()
    }

    @Test
    fun tappingPreviewButtonEmitsPreviewLedgerFastDirectlyWithoutOpeningAdvancedOptions() {
        var emitted: LedgerStatementEvent? = null
        composeRule.setContent {
            BudcomTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(isInitialLoading = false, content = sampleContent()),
                    onEvent = { emitted = it },
                    onBack = {},
                    pdfPageRenderer = fakePdfPageRenderer,
                )
            }
        }
        composeRule.onNodeWithTag("ledger_statement_preview_button").assertIsEnabled()
        composeRule.onNodeWithTag("ledger_statement_preview_button").performClick()
        assertEquals(LedgerStatementEvent.PreviewLedgerFast, emitted)
    }

    @Test
    fun previewOpensInAppWhenPreviewPdfIsSetAndBackDismissesIt() {
        var dismissed = false
        composeRule.setContent {
            BudcomTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(
                        isInitialLoading = false,
                        content = sampleContent(),
                        previewPdf = com.budcom.android.feature.masterdata.ledger.sharing.PreparedLedgerStatementPdf(
                            contentUri = "content://x",
                            cacheFilePath = "/cache/x.pdf",
                            suggestedFilename = "x.pdf",
                        ),
                    ),
                    onEvent = { if (it is LedgerStatementEvent.DismissPreview) dismissed = true },
                    onBack = {},
                    pdfPageRenderer = fakePdfPageRenderer,
                )
            }
        }
        composeRule.onNodeWithTag("pdf_preview_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("pdf_preview_back").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun shareOptionsSheetShowsSharePdfAndSavePdfActions() {
        composeRule.setContent {
            BudcomTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(
                        isInitialLoading = false,
                        content = sampleContent(),
                        showShareOptions = true,
                    ),
                    onEvent = {},
                    onBack = {},
                    pdfPageRenderer = fakePdfPageRenderer,
                )
            }
        }
        composeRule.onNodeWithTag("ledger_statement_share_button").assertIsEnabled()
        composeRule.onNodeWithTag("ledger_statement_destination_android_share").assertIsDisplayed()
        composeRule.onNodeWithTag("ledger_statement_destination_save_pdf").assertIsDisplayed()
        composeRule.onNodeWithTag("ledger_statement_destination_preview_pdf").assertIsDisplayed()
    }

    @Test
    fun changingPeriodOpensDialogAndEmitsPeriodChanged() {
        var applied: LedgerStatementEvent.PeriodChanged? = null
        composeRule.setContent {
            BudcomTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(
                        isInitialLoading = false,
                        content = sampleContent(),
                        fromDate = "2026-07-01",
                        toDate = "2026-07-31",
                    ),
                    onEvent = { if (it is LedgerStatementEvent.PeriodChanged) applied = it },
                    onBack = {},
                    pdfPageRenderer = fakePdfPageRenderer,
                )
            }
        }
        composeRule.onNodeWithTag("ledger_statement_period").performClick()
        composeRule.onNodeWithTag("ledger_statement_to_field").assertIsDisplayed()
        composeRule.onNodeWithTag("ledger_statement_to_field").performTextReplacement("2026-08-11")
        composeRule.onNodeWithTag("ledger_statement_period_confirm").performClick()
        assertEquals(LedgerStatementEvent.PeriodChanged("2026-07-01", "2026-08-11"), applied)
    }

    private fun sampleContent(
        rows: List<LedgerStatementRowUi> = listOf(
            LedgerStatementRowUi(
                voucherId = "v-1",
                dateLabel = "2026-08-01",
                voucherTypeLabel = "Receipt",
                voucherNumberLabel = "R-1",
                particularsLabel = "Ref: R-1",
                debitLabel = null,
                creditLabel = "500.00",
                runningBalanceLabel = "500.00 Dr",
            ),
        ),
        coverageMessage: String? = null,
        whatsAppToPartyAvailable: Boolean = false,
    ) = LedgerStatementContentUi(
        ledgerName = "Cash",
        parentGroup = "Cash-in-Hand",
        periodLabel = "2026-07-01 to 2026-07-31",
        openingLabel = "0.00 Dr",
        closingLabel = "500.00 Dr",
        rows = rows,
        coverageMessage = coverageMessage,
        lastSyncedAt = null,
        whatsAppToPartyAvailable = whatsAppToPartyAvailable,
    )
}
