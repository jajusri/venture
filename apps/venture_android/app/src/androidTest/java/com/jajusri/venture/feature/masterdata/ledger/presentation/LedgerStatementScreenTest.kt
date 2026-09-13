package com.jajusri.venture.feature.masterdata.ledger.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.jajusri.venture.core.pdf.PdfPageRenderer
import com.jajusri.venture.core.pdf.PdfPreviewDocument
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatementMode
import com.jajusri.venture.feature.masterdata.presentation.MasterDataUiError
import com.jajusri.venture.ui.theme.VentureTheme
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
            VentureTheme {
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
            VentureTheme {
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
            VentureTheme {
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
            VentureTheme {
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
            VentureTheme {
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
            VentureTheme {
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
            VentureTheme {
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
            VentureTheme {
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
            VentureTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(
                        isInitialLoading = false,
                        content = sampleContent(),
                        previewPdf = com.jajusri.venture.feature.masterdata.ledger.sharing.PreparedLedgerStatementPdf(
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
    fun tappingShareButtonOpensTwoOptionMenuWithoutAnyLongPress() {
        composeRule.setContent {
            VentureTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(isInitialLoading = false, content = sampleContent()),
                    onEvent = {},
                    onBack = {},
                    pdfPageRenderer = fakePdfPageRenderer,
                )
            }
        }
        composeRule.onNodeWithTag("ledger_statement_share_button").performClick()
        composeRule.onNodeWithTag("ledger_statement_share_menu_summary").assertIsDisplayed()
        composeRule.onNodeWithTag("ledger_statement_share_menu_detailed").assertIsDisplayed()
    }

    @Test
    fun tappingLedgerSummaryInTheShareMenuEmitsShareLedgerWithModeSummary() {
        var emitted: LedgerStatementEvent? = null
        composeRule.setContent {
            VentureTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(isInitialLoading = false, content = sampleContent()),
                    onEvent = { emitted = it },
                    onBack = {},
                    pdfPageRenderer = fakePdfPageRenderer,
                )
            }
        }
        composeRule.onNodeWithTag("ledger_statement_share_button").performClick()
        composeRule.onNodeWithTag("ledger_statement_share_menu_summary").performClick()
        assertEquals(LedgerStatementEvent.ShareLedgerWithMode(LedgerStatementMode.Summary), emitted)
    }

    @Test
    fun tappingDetailedLedgerInTheShareMenuEmitsShareLedgerWithModeDetailed() {
        var emitted: LedgerStatementEvent? = null
        composeRule.setContent {
            VentureTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(isInitialLoading = false, content = sampleContent()),
                    onEvent = { emitted = it },
                    onBack = {},
                    pdfPageRenderer = fakePdfPageRenderer,
                )
            }
        }
        composeRule.onNodeWithTag("ledger_statement_share_button").performClick()
        composeRule.onNodeWithTag("ledger_statement_share_menu_detailed").performClick()
        assertEquals(LedgerStatementEvent.ShareLedgerWithMode(LedgerStatementMode.Detailed), emitted)
    }

    @Test
    fun tappingMoreOptionsInTheShareMenuOpensTheAdvancedOptionsSheet() {
        var emitted: LedgerStatementEvent? = null
        composeRule.setContent {
            VentureTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(isInitialLoading = false, content = sampleContent()),
                    onEvent = { emitted = it },
                    onBack = {},
                    pdfPageRenderer = fakePdfPageRenderer,
                )
            }
        }
        composeRule.onNodeWithTag("ledger_statement_share_button").performClick()
        composeRule.onNodeWithTag("ledger_statement_share_menu_more").performClick()
        assertEquals(LedgerStatementEvent.OpenShareOptions, emitted)
    }

    @Test
    fun longPressOnShareButtonStillOpensAdvancedOptionsDirectly() {
        // Preserved as a shortcut for muscle memory, but no longer required for anything —
        // Summary/Detailed are already reachable through a normal tap (see the menu tests above).
        var emitted: LedgerStatementEvent? = null
        composeRule.setContent {
            VentureTheme {
                LedgerStatementScreen(
                    state = LedgerStatementUiState(isInitialLoading = false, content = sampleContent()),
                    onEvent = { emitted = it },
                    onBack = {},
                    pdfPageRenderer = fakePdfPageRenderer,
                )
            }
        }
        composeRule.onNodeWithTag("ledger_statement_share_button").performTouchInput { longClick() }
        assertEquals(LedgerStatementEvent.OpenShareOptions, emitted)
    }

    @Test
    fun shareOptionsSheetShowsSharePdfAndSavePdfActions() {
        composeRule.setContent {
            VentureTheme {
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
        // ModalBottomSheet's content Column has no scroll of its own (confirmed: performScrollTo
        // throws "no parent layout with a Scroll SemanticsAction" here) -- Material3's default
        // SheetState starts PartiallyExpanded, not Expanded, and this sheet's stacked content
        // (period chips + statement-mode row + five share destinations) is taller than the peek
        // height on this device, hiding the last destination(s) until the sheet is dragged open
        // the rest of the way -- exactly what a real user would do. Simulate that swipe rather
        // than assert on a state no real interaction ever produces.
        composeRule.onNodeWithTag("ledger_statement_share_button").assertIsEnabled()
        // ModalBottomSheet renders in its own Popup window, so onRoot() is ambiguous (finds both
        // the main screen's root and the sheet's) -- swipe on the sheet's own content container.
        composeRule.onNodeWithTag("ledger_statement_advanced_options").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("ledger_statement_destination_android_share").assertIsDisplayed()
        composeRule.onNodeWithTag("ledger_statement_destination_save_pdf").assertIsDisplayed()
        composeRule.onNodeWithTag("ledger_statement_destination_preview_pdf").assertIsDisplayed()
    }

    @Test
    fun changingPeriodOpensDialogAndEmitsPeriodChanged() {
        var applied: LedgerStatementEvent.PeriodChanged? = null
        composeRule.setContent {
            VentureTheme {
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
        // ledger_statement_period is a plain Text leaf inside a Row with its own onClick
        // (Modifier.clickable), which makes Compose merge descendants -- the same pattern already
        // fixed for Connect's Alias line (Phase 51): useUnmergedTree = true reaches the leaf's own
        // testTag directly. A touch at the Text's on-screen bounds still lands inside the parent
        // Row's clickable area, so the click correctly reaches onChangePeriod.
        composeRule.onNodeWithTag("ledger_statement_period", useUnmergedTree = true).performClick()
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
