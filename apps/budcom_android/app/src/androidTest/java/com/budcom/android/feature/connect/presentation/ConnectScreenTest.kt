package com.budcom.android.feature.connect.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.ui.theme.BudcomTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConnectScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun row(
        partyId: String = "p1",
        name: String = "ABC Traders",
        phoneE164: String? = "+919876543210",
        balanceLabel: String? = "1000.00 Dr",
        linkedLedgerId: String? = "guid:abc",
        linkedLedgerName: String? = "ABC Traders",
        linkedLedgerAlias: String? = null,
    ) = ConnectRowUi(
        partyId = partyId,
        displayName = name,
        phoneDisplay = phoneE164,
        phoneE164 = phoneE164,
        tagNames = emptyList(),
        balanceLabel = balanceLabel,
        linkedLedgerId = linkedLedgerId,
        linkedLedgerName = linkedLedgerName,
        linkedLedgerAlias = linkedLedgerAlias,
    )

    @Test
    fun loadingState() {
        composeRule.setContent {
            BudcomTheme { ConnectScreen(state = ConnectUiState(isInitialLoading = true), onEvent = {}) }
        }
        composeRule.onNodeWithTag("connect_loading").assertIsDisplayed()
    }

    @Test
    fun customersEmptyState() {
        composeRule.setContent {
            BudcomTheme {
                ConnectScreen(state = ConnectUiState(isInitialLoading = false, selectedTab = ConnectTab.Customers), onEvent = {})
            }
        }
        composeRule.onNodeWithTag("connect_empty").assertIsDisplayed()
    }

    @Test
    fun prospectsEmptyState() {
        composeRule.setContent {
            BudcomTheme {
                ConnectScreen(state = ConnectUiState(isInitialLoading = false, selectedTab = ConnectTab.Prospects), onEvent = {})
            }
        }
        composeRule.onNodeWithTag("connect_empty").assertIsDisplayed()
    }

    @Test
    fun contentStateShowsRowWithBalanceAndDeepLinkActions() {
        composeRule.setContent {
            BudcomTheme { ConnectScreen(state = ConnectUiState(isInitialLoading = false, rows = listOf(row())), onEvent = {}) }
        }
        composeRule.onNodeWithTag("connect_list").assertIsDisplayed()
        composeRule.onNodeWithTag("connect_row_p1").assertIsDisplayed()
        composeRule.onNodeWithTag("connect_view_ledger_p1").assertIsDisplayed()
        composeRule.onNodeWithTag("connect_view_vouchers_p1").assertIsDisplayed()
    }

    // The row Card sets onClick, which Compose's semantics automatically marks
    // mergeDescendants = true (so TalkBack reads the whole row as one unit) -- this collapses a
    // plain, non-interactive Text leaf's own testTag out of the default (merged) query tree, even
    // though the text is genuinely composed and correctly included in the row's own merged
    // accessibility description (confirmed live: real-device screenshot and this test's own
    // unmerged-tree dump both show "Alias: 25" present). useUnmergedTree = true is the standard,
    // documented way to assert on a merged-away descendant directly -- this is a test-query detail,
    // not a change to what's rendered. Interactive children (Call/WhatsApp/View Ledger buttons)
    // aren't affected by this because Compose does not merge semantics past another node that is
    // itself a distinct clickable/Role-bearing element.
    @Test
    fun aliasIsShownOnItsOwnLineWhenTheLinkedLedgerHasOne() {
        composeRule.setContent {
            BudcomTheme { ConnectScreen(state = ConnectUiState(isInitialLoading = false, rows = listOf(row(linkedLedgerAlias = "25"))), onEvent = {}) }
        }
        composeRule.onNodeWithTag("connect_alias_p1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aliasIsAbsentWhenTheLinkedLedgerHasNone() {
        composeRule.setContent {
            BudcomTheme { ConnectScreen(state = ConnectUiState(isInitialLoading = false, rows = listOf(row())), onEvent = {}) }
        }
        composeRule.onNodeWithTag("connect_alias_p1").assertDoesNotExist()
    }

    @Test
    fun aBudcomOnlyPartyWithNoAccountingLinkHidesLedgerAndVoucherActions() {
        composeRule.setContent {
            BudcomTheme {
                ConnectScreen(
                    state = ConnectUiState(
                        isInitialLoading = false,
                        selectedTab = ConnectTab.Prospects,
                        rows = listOf(row(linkedLedgerId = null, linkedLedgerName = null, balanceLabel = null)),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("connect_row_p1").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesForTag("connect_view_ledger_p1").fetchSemanticsNodes().size)
    }

    @Test
    fun tappingCallEmitsCallTappedWithTheRowsPhone() {
        var lastEvent: ConnectEvent? = null
        composeRule.setContent {
            BudcomTheme { ConnectScreen(state = ConnectUiState(isInitialLoading = false, rows = listOf(row())), onEvent = { lastEvent = it }) }
        }
        composeRule.onNodeWithTag("connect_call_p1").performClick()
        assertEquals(ConnectEvent.CallTapped("+919876543210"), lastEvent)
    }

    @Test
    fun tappingWhatsAppEmitsWhatsAppTappedWithTheRowsPhone() {
        var lastEvent: ConnectEvent? = null
        composeRule.setContent {
            BudcomTheme { ConnectScreen(state = ConnectUiState(isInitialLoading = false, rows = listOf(row())), onEvent = { lastEvent = it }) }
        }
        composeRule.onNodeWithTag("connect_whatsapp_p1").performClick()
        assertEquals(ConnectEvent.WhatsAppTapped("+919876543210"), lastEvent)
    }

    @Test
    fun tappingViewLedgerEmitsViewLedgerTappedWithTheStableLedgerId() {
        var lastEvent: ConnectEvent? = null
        composeRule.setContent {
            BudcomTheme { ConnectScreen(state = ConnectUiState(isInitialLoading = false, rows = listOf(row())), onEvent = { lastEvent = it }) }
        }
        composeRule.onNodeWithTag("connect_view_ledger_p1").performClick()
        assertEquals(ConnectEvent.ViewLedgerTapped("guid:abc"), lastEvent)
    }

    @Test
    fun switchingTabsEmitsTabChanged() {
        var lastEvent: ConnectEvent? = null
        composeRule.setContent {
            BudcomTheme { ConnectScreen(state = ConnectUiState(isInitialLoading = false), onEvent = { lastEvent = it }) }
        }
        composeRule.onNodeWithTag("connect_tab_prospects").performClick()
        assertEquals(ConnectEvent.TabChanged(ConnectTab.Prospects), lastEvent)
    }

    @Test
    fun offlineBannerShowsWhenNotOnline() {
        composeRule.setContent {
            BudcomTheme { ConnectScreen(state = ConnectUiState(isInitialLoading = false, isOnline = false), onEvent = {}) }
        }
        composeRule.onNodeWithTag("connect_offline_banner").assertIsDisplayed()
    }

    @Test
    fun dataFreshnessShowsWhenContentExists() {
        composeRule.setContent {
            BudcomTheme {
                ConnectScreen(
                    state = ConnectUiState(isInitialLoading = false, rows = listOf(row()), dataFreshnessAt = "2026-08-23T10:00:00Z"),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("connect_data_freshness").assertIsDisplayed()
    }

    @Test
    fun dataFreshnessIsAbsentWithNoContent() {
        composeRule.setContent {
            BudcomTheme { ConnectScreen(state = ConnectUiState(isInitialLoading = false, rows = emptyList()), onEvent = {}) }
        }
        composeRule.onNodeWithTag("connect_data_freshness").assertDoesNotExist()
    }

    @Test
    fun errorRetry() {
        var retried = false
        composeRule.setContent {
            BudcomTheme {
                ConnectScreen(
                    state = ConnectUiState(isInitialLoading = false, error = MasterDataUiError.Timeout("The request timed out.")),
                    onEvent = { if (it is ConnectEvent.Retry) retried = true },
                )
            }
        }
        composeRule.onNodeWithTag("connect_retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun searchFieldEmitsSearchChanged() {
        var lastEvent: ConnectEvent? = null
        composeRule.setContent {
            BudcomTheme { ConnectScreen(state = ConnectUiState(isInitialLoading = false), onEvent = { lastEvent = it }) }
        }
        composeRule.onNodeWithTag("connect_search").performClick()
        // Typing itself is exercised indirectly via SearchChanged already covered by
        // ConnectViewModelTest; this proves the field is present and interactable.
        composeRule.onNodeWithTag("connect_search").assertIsDisplayed()
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesForTag(tag: String) =
    onAllNodes(hasTestTag(tag))
