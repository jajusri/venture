package com.budcom.android.feature.dincharya.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.budcom.android.feature.dincharya.domain.model.FollowUpUrgency
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.ui.theme.BudcomTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DincharyaScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun followUp(
        noteId: String = "note-1",
        partyId: String = "p1",
        name: String = "ABC Traders",
        body: String = "Follow up on delayed payment",
        urgency: FollowUpUrgency = FollowUpUrgency.Overdue,
    ) = FollowUpUi(noteId = noteId, partyId = partyId, partyDisplayName = name, body = body, dueAt = 1_700_000_000_000L, urgency = urgency)

    private fun confirmation(partyId: String = "p2", name: String = "XYZ Suppliers", fields: String = "Phone, Email") =
        PendingConfirmationUi(partyId = partyId, partyDisplayName = name, fieldsLabel = fields)

    private fun contact(partyId: String = "p3", name: String = "New Prospect Traders") =
        PendingContactCompletionUi(partyId = partyId, partyDisplayName = name)

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesForTag(tag: String) =
        onAllNodes(hasTestTag(tag))

    @Test
    fun loadingState() {
        composeRule.setContent {
            BudcomTheme { DincharyaScreen(state = DincharyaUiState(isInitialLoading = true), onEvent = {}) }
        }
        composeRule.onNodeWithTag("dincharya_loading").assertIsDisplayed()
    }

    @Test
    fun errorStateShowsRetryAction() {
        var lastEvent: DincharyaEvent? = null
        composeRule.setContent {
            BudcomTheme {
                DincharyaScreen(
                    state = DincharyaUiState(isInitialLoading = false, error = MasterDataUiError.Unexpected("boom")),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("dincharya_error").assertIsDisplayed()
        composeRule.onNodeWithTag("dincharya_retry").performClick()
        assertEquals(DincharyaEvent.Retry, lastEvent)
    }

    @Test
    fun emptyStateIsShownAsAGenuinePositiveMessage() {
        composeRule.setContent {
            BudcomTheme { DincharyaScreen(state = DincharyaUiState(isInitialLoading = false), onEvent = {}) }
        }
        composeRule.onNodeWithTag("dincharya_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("dincharya_oi_framing").assertIsDisplayed()
    }

    @Test
    fun followUpSectionRendersAndTapNavigatesToThatPartysDetail() {
        var lastEvent: DincharyaEvent? = null
        composeRule.setContent {
            BudcomTheme {
                DincharyaScreen(
                    state = DincharyaUiState(isInitialLoading = false, followUps = listOf(followUp())),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("dincharya_section_followups").assertIsDisplayed()
        composeRule.onNodeWithTag("dincharya_followup_note-1").assertIsDisplayed()
        composeRule.onNodeWithTag("dincharya_followup_note-1").performClick()
        assertEquals(DincharyaEvent.ItemTapped("p1"), lastEvent)
    }

    @Test
    fun pendingConfirmationSectionRendersAndTapNavigates() {
        var lastEvent: DincharyaEvent? = null
        composeRule.setContent {
            BudcomTheme {
                DincharyaScreen(
                    state = DincharyaUiState(isInitialLoading = false, pendingConfirmations = listOf(confirmation())),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("dincharya_section_confirmations").assertIsDisplayed()
        composeRule.onNodeWithTag("dincharya_confirmation_p2").assertIsDisplayed()
        composeRule.onNodeWithTag("dincharya_confirmation_p2").performClick()
        assertEquals(DincharyaEvent.ItemTapped("p2"), lastEvent)
    }

    @Test
    fun pendingContactCompletionSectionRendersAndTapNavigates() {
        var lastEvent: DincharyaEvent? = null
        composeRule.setContent {
            BudcomTheme {
                DincharyaScreen(
                    state = DincharyaUiState(isInitialLoading = false, pendingContactCompletions = listOf(contact())),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("dincharya_section_contact").assertIsDisplayed()
        composeRule.onNodeWithTag("dincharya_contact_p3").assertIsDisplayed()
        composeRule.onNodeWithTag("dincharya_contact_p3").performClick()
        assertEquals(DincharyaEvent.ItemTapped("p3"), lastEvent)
    }

    @Test
    fun allThreeSectionsCanRenderSimultaneouslyWithoutInterferingWithEachOther() {
        composeRule.setContent {
            BudcomTheme {
                DincharyaScreen(
                    state = DincharyaUiState(
                        isInitialLoading = false,
                        followUps = listOf(followUp()),
                        pendingConfirmations = listOf(confirmation()),
                        pendingContactCompletions = listOf(contact()),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("dincharya_followup_note-1").assertIsDisplayed()
        composeRule.onNodeWithTag("dincharya_confirmation_p2").assertIsDisplayed()
        composeRule.onNodeWithTag("dincharya_contact_p3").assertIsDisplayed()
    }

    @Test
    fun sameCompanyPartyAppearingInTwoDifferentGroupsProducesNoDuplicateNodeKeyCrash() {
        // A Party can genuinely have both a pending Tally confirmation and missing contact info at
        // once — the same partyId must be safely usable as part of two different LazyColumn item
        // keys (each is prefixed by item type) without a duplicate-key crash.
        composeRule.setContent {
            BudcomTheme {
                DincharyaScreen(
                    state = DincharyaUiState(
                        isInitialLoading = false,
                        pendingConfirmations = listOf(confirmation(partyId = "shared-party")),
                        pendingContactCompletions = listOf(contact(partyId = "shared-party")),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("dincharya_confirmation_shared-party").assertIsDisplayed()
        composeRule.onNodeWithTag("dincharya_contact_shared-party").assertIsDisplayed()
    }

    @Test
    fun moreCountDisclosureIsShownOnlyWhenThereAreMoreItemsThanDisplayed() {
        composeRule.setContent {
            BudcomTheme {
                DincharyaScreen(
                    state = DincharyaUiState(isInitialLoading = false, followUps = listOf(followUp()), followUpsMoreCount = 3),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("dincharya_followups_more").assertIsDisplayed()
    }

    @Test
    fun noMoreCountDisclosureWhenEveryItemIsAlreadyShown() {
        composeRule.setContent {
            BudcomTheme {
                DincharyaScreen(
                    state = DincharyaUiState(isInitialLoading = false, followUps = listOf(followUp()), followUpsMoreCount = 0),
                    onEvent = {},
                )
            }
        }
        assertEquals(0, composeRule.onAllNodesForTag("dincharya_followups_more").fetchSemanticsNodes().size)
    }

    @Test
    fun eachFollowUpUrgencyRendersItsOwnPlainLanguageReason() {
        composeRule.setContent {
            BudcomTheme {
                DincharyaScreen(
                    state = DincharyaUiState(
                        isInitialLoading = false,
                        followUps = listOf(
                            followUp(noteId = "note-overdue", urgency = FollowUpUrgency.Overdue),
                            followUp(noteId = "note-today", urgency = FollowUpUrgency.DueToday),
                            followUp(noteId = "note-upcoming", urgency = FollowUpUrgency.Upcoming),
                        ),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("dincharya_followup_note-overdue").assertIsDisplayed()
        composeRule.onNodeWithTag("dincharya_followup_note-today").assertIsDisplayed()
        composeRule.onNodeWithTag("dincharya_followup_note-upcoming").assertIsDisplayed()
    }

    @Test
    fun longPartyNameAndLongNoteBodyRenderWithoutCrashing() {
        val longName = "A Very Long Registered Business Name That Keeps Going And Going Pvt Ltd " + "X".repeat(200)
        val longBody = "This is an extremely long follow-up note body. ".repeat(50)
        composeRule.setContent {
            BudcomTheme {
                DincharyaScreen(
                    state = DincharyaUiState(
                        isInitialLoading = false,
                        followUps = listOf(followUp(name = longName, body = longBody)),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("dincharya_followup_note-1").assertIsDisplayed()
    }
}
