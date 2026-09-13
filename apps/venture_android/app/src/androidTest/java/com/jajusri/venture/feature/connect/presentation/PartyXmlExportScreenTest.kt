package com.jajusri.venture.feature.connect.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jajusri.venture.feature.masterdata.presentation.MasterDataUiError
import com.jajusri.venture.feature.party.domain.model.FieldProvenanceState
import com.jajusri.venture.feature.party.domain.model.PartyExportEvent
import com.jajusri.venture.feature.party.domain.model.PartyFieldNames
import com.jajusri.venture.feature.party.domain.model.TallyFieldExportCandidate
import com.jajusri.venture.ui.theme.VentureTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PartyXmlExportScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun candidate(
        fieldName: String = PartyFieldNames.GSTIN,
        label: String = "GSTIN",
        ventureValue: String? = "29ABCDE1234F1Z5",
        tallyValue: String? = null,
        state: FieldProvenanceState = FieldProvenanceState.VentureOnlyPending,
    ) = TallyFieldExportCandidate(fieldName, label, tallyValue, ventureValue, state)

    @Test
    fun loadingState() {
        composeRule.setContent {
            VentureTheme { PartyXmlExportScreen(state = PartyXmlExportUiState(isLoading = true), onEvent = {}) }
        }
        composeRule.onNodeWithTag("party_xml_export_loading").assertIsDisplayed()
    }

    @Test
    fun errorStateRetryEmitsRetry() {
        var retried = false
        composeRule.setContent {
            VentureTheme {
                PartyXmlExportScreen(
                    state = PartyXmlExportUiState(isLoading = false, error = MasterDataUiError.Message("No linked Tally ledger.")),
                    onEvent = { if (it is PartyXmlExportEvent.Retry) retried = true },
                )
            }
        }
        composeRule.onNodeWithTag("party_xml_export_error").assertIsDisplayed()
        composeRule.onNodeWithTag("party_xml_export_retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun tappingAFieldCheckboxEmitsFieldSelectionToggled() {
        var lastEvent: PartyXmlExportEvent? = null
        composeRule.setContent {
            VentureTheme {
                PartyXmlExportScreen(
                    state = PartyXmlExportUiState(isLoading = false, candidates = listOf(candidate())),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("party_xml_export_field_${PartyFieldNames.GSTIN}_checkbox").performClick()
        assertEquals(PartyXmlExportEvent.FieldSelectionToggled(PartyFieldNames.GSTIN), lastEvent)
    }

    @Test
    fun generateIsDisabledWithNoFieldSelected() {
        composeRule.setContent {
            VentureTheme {
                PartyXmlExportScreen(
                    state = PartyXmlExportUiState(isLoading = false, candidates = listOf(candidate()), selectedFieldNames = emptySet()),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("party_xml_export_generate").assertIsNotEnabled()
    }

    @Test
    fun generateIsEnabledOnceAFieldIsSelectedAndTapEmitsGenerateTapped() {
        var lastEvent: PartyXmlExportEvent? = null
        composeRule.setContent {
            VentureTheme {
                PartyXmlExportScreen(
                    state = PartyXmlExportUiState(
                        isLoading = false,
                        candidates = listOf(candidate()),
                        selectedFieldNames = setOf(PartyFieldNames.GSTIN),
                    ),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("party_xml_export_generate").assertIsEnabled()
        composeRule.onNodeWithTag("party_xml_export_generate").performClick()
        assertEquals(PartyXmlExportEvent.GenerateTapped, lastEvent)
    }

    @Test
    fun checkTallyIsDisabledWhenNothingIsExportedYet() {
        composeRule.setContent {
            VentureTheme {
                PartyXmlExportScreen(
                    state = PartyXmlExportUiState(isLoading = false, candidates = listOf(candidate(state = FieldProvenanceState.VentureOnlyPending))),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("party_xml_export_check_tally").assertIsNotEnabled()
    }

    @Test
    fun checkTallyIsEnabledOnceAFieldIsExportedAndTapEmitsCheckTallyTapped() {
        var lastEvent: PartyXmlExportEvent? = null
        composeRule.setContent {
            VentureTheme {
                PartyXmlExportScreen(
                    state = PartyXmlExportUiState(isLoading = false, candidates = listOf(candidate(state = FieldProvenanceState.Exported))),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("party_xml_export_check_tally").assertIsEnabled()
        composeRule.onNodeWithTag("party_xml_export_check_tally").performClick()
        assertEquals(PartyXmlExportEvent.CheckTallyTapped, lastEvent)
    }

    @Test
    fun exportHistoryIsShownWhenPresent() {
        composeRule.setContent {
            VentureTheme {
                PartyXmlExportScreen(
                    state = PartyXmlExportUiState(
                        isLoading = false,
                        candidates = listOf(candidate()),
                        history = listOf(PartyExportEvent("co-1", "exp-1", "party-1", 0L, "VENTURE-Tally-Export-ABC.xml", listOf(PartyFieldNames.GSTIN))),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("party_xml_export_history_exp-1").assertIsDisplayed()
    }

    @Test
    fun noHistoryShowsNoHistoryList() {
        composeRule.setContent {
            VentureTheme {
                PartyXmlExportScreen(
                    state = PartyXmlExportUiState(isLoading = false, candidates = listOf(candidate()), history = emptyList()),
                    onEvent = {},
                )
            }
        }
        assertEquals(0, composeRule.onAllNodesForTag("party_xml_export_history_list").fetchSemanticsNodes().size)
    }

    @Test
    fun noticeDialogDismissEmitsDismissNotice() {
        var lastEvent: PartyXmlExportEvent? = null
        composeRule.setContent {
            VentureTheme {
                PartyXmlExportScreen(
                    state = PartyXmlExportUiState(isLoading = false, candidates = listOf(candidate()), notice = "Tally export XML saved."),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("party_xml_export_notice_message").assertIsDisplayed()
        composeRule.onNodeWithTag("party_xml_export_notice_dismiss").performClick()
        assertEquals(PartyXmlExportEvent.DismissNotice, lastEvent)
    }

    @Test
    fun aConflictedFieldShowsNeedsReviewState() {
        composeRule.setContent {
            VentureTheme {
                PartyXmlExportScreen(
                    state = PartyXmlExportUiState(isLoading = false, candidates = listOf(candidate(state = FieldProvenanceState.Conflict, tallyValue = "27XYZAB5678C1Z9"))),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("party_xml_export_field_${PartyFieldNames.GSTIN}_state").assertIsDisplayed()
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesForTag(tag: String) =
    onAllNodes(hasTestTag(tag))
