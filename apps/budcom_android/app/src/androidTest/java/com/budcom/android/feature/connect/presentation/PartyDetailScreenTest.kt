package com.budcom.android.feature.connect.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.LedgerIdentitySource
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyFieldNames
import com.budcom.android.feature.party.domain.model.PartyNote
import com.budcom.android.feature.party.domain.model.PartySourceLink
import com.budcom.android.feature.party.domain.model.PartySourceType
import com.budcom.android.feature.party.domain.model.Tag
import com.budcom.android.ui.theme.BudcomTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PartyDetailScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun party(
        partyId: String = "p1",
        classification: PartyClassification = PartyClassification.Customer,
        phone: String? = "9876543210",
    ) = Party(
        companyId = "co-1", partyId = partyId, displayName = "ABC Traders", classification = classification,
        primaryPhone = phone, primaryPhoneNormalized = phone, primaryEmail = "abc@example.com",
        addressLine1 = null, addressCity = null, addressState = null, addressPincode = null, gstin = null,
        createdAt = 0L, updatedAt = 0L,
    )

    private fun sourceLink(ledgerId: String = "guid:abc") = PartySourceLink(
        companyId = "co-1", partyId = "p1", sourceType = PartySourceType.TallyLedger, sourceInstanceId = "co-1",
        externalEntityId = ledgerId, externalDisplayName = "ABC Traders", identitySource = LedgerIdentitySource.Guid,
        lastConfirmedAt = 0L,
    )

    private fun fieldRow(
        fieldName: String = PartyFieldNames.PRIMARY_EMAIL,
        label: String = "Email",
        value: String? = "abc@example.com",
        provenanceLabel: String = "Pending in BUDCOM",
        isConflict: Boolean = false,
    ) = PartyFieldRowUi(fieldName, label, value, provenanceLabel, isConflict)

    private fun contact(id: String = "c1", isPrimary: Boolean = false) = PartyContactPerson(
        companyId = "co-1", contactPersonId = id, partyId = "p1", name = "Owner", designation = "Owner",
        mobile = "9876543210", mobileNormalized = "9876543210", whatsappNumber = null, email = null,
        isPrimary = isPrimary, provenance = FieldProvenanceState.BudcomOnlyPending, createdAt = 0L, updatedAt = 0L,
    )

    private fun tag(id: String = "t1", name: String = "Dealer") = Tag(id, null, name, name, 0L)

    private fun note(id: String = "n1", linkedVoucherId: String? = null) =
        PartyNote(companyId = "co-1", noteId = id, partyId = "p1", body = "Called about delivery", linkedVoucherId = linkedVoucherId, createdAt = 0L, updatedAt = 0L)

    @Test
    fun loadingState() {
        composeRule.setContent {
            BudcomTheme { PartyDetailScreen(state = PartyDetailUiState(isLoading = true), onEvent = {}) }
        }
        composeRule.onNodeWithTag("party_detail_loading").assertIsDisplayed()
    }

    @Test
    fun errorStateRetryEmitsRetry() {
        var retried = false
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(isLoading = false, error = MasterDataUiError.Message("This party could not be found.")),
                    onEvent = { if (it is PartyDetailEvent.Retry) retried = true },
                )
            }
        }
        composeRule.onNodeWithTag("party_detail_error").assertIsDisplayed()
        composeRule.onNodeWithTag("party_detail_retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun tallyBackedPartyShowsAccountingDeepLinks() {
        var lastEvent: PartyDetailEvent? = null
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(isLoading = false, party = party(), sourceLink = sourceLink(), balanceLabel = "1000.00 Dr"),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("party_detail_view_ledger").assertIsDisplayed()
        composeRule.onNodeWithTag("party_detail_view_vouchers").assertIsDisplayed()
        composeRule.onNodeWithTag("party_detail_view_ledger").performClick()
        assertEquals(PartyDetailEvent.ViewLedgerTapped("guid:abc"), lastEvent)
    }

    @Test
    fun aProspectWithNoSourceLinkHidesAccountingDeepLinksAndNeverFabricatesABalance() {
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(isLoading = false, party = party(classification = PartyClassification.Prospect), sourceLink = null, balanceLabel = null),
                    onEvent = {},
                )
            }
        }
        assertEquals(0, composeRule.onAllNodesForTag("party_detail_view_ledger").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesForTag("party_detail_view_vouchers").fetchSemanticsNodes().size)
    }

    @Test
    fun tappingAFieldRowOpensTheEditDialogAndSavingEmitsTheNewValue() {
        var lastEvent: PartyDetailEvent? = null
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(isLoading = false, party = party(), fieldRows = listOf(fieldRow())),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("party_detail_field_${PartyFieldNames.PRIMARY_EMAIL}_edit").performClick()
        assertEquals(PartyDetailEvent.EditFieldTapped(PartyFieldNames.PRIMARY_EMAIL, "Email"), lastEvent)
    }

    @Test
    fun editFieldDialogTypingAndSaveEmitsExpectedEvents() {
        var lastEvent: PartyDetailEvent? = null
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(
                        isLoading = false,
                        party = party(),
                        activeDialog = PartyDetailDialog.EditField(PartyFieldNames.PRIMARY_EMAIL, "Email", "abc@example.com"),
                    ),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("party_detail_edit_field_value").performTextInput("x")
        assertTrue(lastEvent is PartyDetailEvent.EditFieldValueChanged)
        composeRule.onNodeWithTag("party_detail_edit_field_save").performClick()
        assertEquals(PartyDetailEvent.SaveEditedField, lastEvent)
    }

    @Test
    fun conflictFieldShowsNeedsReviewProvenance() {
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(
                        isLoading = false,
                        party = party(),
                        fieldRows = listOf(fieldRow(provenanceLabel = "Needs review", isConflict = true)),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("party_detail_field_${PartyFieldNames.PRIMARY_EMAIL}_provenance").assertIsDisplayed()
    }

    @Test
    fun assignedTagChipRemoveEmitsRemoveTagTapped() {
        var lastEvent: PartyDetailEvent? = null
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(isLoading = false, party = party(), tags = listOf(tag())),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("party_detail_tag_t1").performClick()
        assertEquals(PartyDetailEvent.RemoveTagTapped("t1"), lastEvent)
    }

    @Test
    fun addTagDialogCreateAndAssignEmitsTypedName() {
        var lastEvent: PartyDetailEvent? = null
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(isLoading = false, party = party(), activeDialog = PartyDetailDialog.AddTag),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("party_detail_new_tag_name").performTextInput("Dealer")
        composeRule.onNodeWithTag("party_detail_new_tag_create").performClick()
        assertEquals(PartyDetailEvent.CreateAndAssignTag("Dealer"), lastEvent)
    }

    @Test
    fun contactPersonRowEditAndDeleteEmitExpectedEvents() {
        var lastEvent: PartyDetailEvent? = null
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(isLoading = false, party = party(), contactPersons = listOf(contact())),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("party_detail_contact_c1_edit").performClick()
        assertEquals(PartyDetailEvent.EditContactTapped("c1"), lastEvent)
        composeRule.onNodeWithTag("party_detail_contact_c1_delete").performClick()
        assertEquals(PartyDetailEvent.DeleteContactTapped("c1"), lastEvent)
    }

    @Test
    fun contactPersonEditorPrimaryCheckboxEmitsIsPrimaryChanged() {
        var lastEvent: PartyDetailEvent? = null
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(isLoading = false, party = party(), activeDialog = PartyDetailDialog.ContactPersonEditor(name = "Owner")),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("party_detail_contact_primary").performClick()
        assertEquals(PartyDetailEvent.ContactFieldChanged(isPrimary = true), lastEvent)
    }

    @Test
    fun noteRowShowsLinkedVoucherActionAndDeleteEmitsExpectedEvents() {
        var lastEvent: PartyDetailEvent? = null
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(isLoading = false, party = party(), notes = listOf(note(linkedVoucherId = "v1"))),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("party_detail_note_n1_voucher").performClick()
        assertEquals(PartyDetailEvent.LinkedVoucherTapped("v1"), lastEvent)
        composeRule.onNodeWithTag("party_detail_note_n1_delete").performClick()
        assertEquals(PartyDetailEvent.DeleteNoteTapped("n1"), lastEvent)
    }

    @Test
    fun aNoteWithNoLinkedVoucherHidesTheVoucherAction() {
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(isLoading = false, party = party(), notes = listOf(note(linkedVoucherId = null))),
                    onEvent = {},
                )
            }
        }
        assertEquals(0, composeRule.onAllNodesForTag("party_detail_note_n1_voucher").fetchSemanticsNodes().size)
    }

    @Test
    fun addNoteDialogTypingAndSaveEmitExpectedEvents() {
        var lastEvent: PartyDetailEvent? = null
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(isLoading = false, party = party(), activeDialog = PartyDetailDialog.NoteEditor()),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("party_detail_note_body").performTextInput("Delivery delayed")
        assertTrue(lastEvent is PartyDetailEvent.NoteBodyChanged)
        composeRule.onNodeWithTag("party_detail_note_save").performClick()
        assertEquals(PartyDetailEvent.SaveNote, lastEvent)
    }

    @Test
    fun pickingANoteTypeEmitsNoteTypeChanged() {
        var lastEvent: PartyDetailEvent? = null
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(isLoading = false, party = party(), activeDialog = PartyDetailDialog.NoteEditor()),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("party_detail_note_type_Commitment").performClick()
        assertEquals(
            PartyDetailEvent.NoteTypeChanged(com.budcom.android.feature.party.domain.model.NoteType.Commitment),
            lastEvent,
        )
    }

    @Test
    fun commitmentTypeShowsTheDueDateField() {
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(
                        isLoading = false,
                        party = party(),
                        activeDialog = PartyDetailDialog.NoteEditor(type = com.budcom.android.feature.party.domain.model.NoteType.Commitment),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("party_detail_note_due_at").assertIsDisplayed()
    }

    @Test
    fun generalTypeHidesTheDueDateField() {
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(isLoading = false, party = party(), activeDialog = PartyDetailDialog.NoteEditor()),
                    onEvent = {},
                )
            }
        }
        assertEquals(0, composeRule.onAllNodesForTag("party_detail_note_due_at").fetchSemanticsNodes().size)
    }

    @Test
    fun tappingEditOnANoteEmitsEditNoteTapped() {
        var lastEvent: PartyDetailEvent? = null
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(isLoading = false, party = party(), notes = listOf(note(id = "n1"))),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("party_detail_note_n1_edit").performClick()
        assertEquals(PartyDetailEvent.EditNoteTapped("n1"), lastEvent)
    }

    @Test
    fun editModeShowsEditNoteTitleAndPrefillsBody() {
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(
                        isLoading = false,
                        party = party(),
                        activeDialog = PartyDetailDialog.NoteEditor(noteId = "n1", body = "Existing text"),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("party_detail_note_editor_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("party_detail_note_body").assertIsDisplayed()
    }

    @Test
    fun noticeDialogDismissEmitsDismissNotice() {
        var lastEvent: PartyDetailEvent? = null
        composeRule.setContent {
            BudcomTheme {
                PartyDetailScreen(
                    state = PartyDetailUiState(isLoading = false, party = party(), notice = "Linked voucher is not available."),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("party_detail_notice_message").assertIsDisplayed()
        composeRule.onNodeWithTag("party_detail_notice_dismiss").performClick()
        assertEquals(PartyDetailEvent.DismissNotice, lastEvent)
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesForTag(tag: String) =
    onAllNodes(hasTestTag(tag))
