package com.jajusri.venture.feature.search.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jajusri.venture.feature.masterdata.presentation.MasterDataUiError
import com.jajusri.venture.feature.search.domain.model.SearchSection
import com.jajusri.venture.ui.theme.VentureTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UniversalSearchScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun idleAndSearchField() {
        composeRule.setContent {
            VentureTheme {
                UniversalSearchScreen(state = UniversalSearchUiState(), onEvent = {})
            }
        }
        composeRule.onNodeWithTag("universal_search_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("universal_search_field").assertIsDisplayed()
        composeRule.onNodeWithTag("universal_search_idle").assertIsDisplayed()
        composeRule.onNodeWithTag("universal_search_subtitle").assertIsDisplayed()
    }

    @Test
    fun loadingSections() {
        composeRule.setContent {
            VentureTheme {
                UniversalSearchScreen(
                    state = UniversalSearchUiState(
                        query = "cash",
                        isSearching = true,
                        hasSearched = true,
                        activeQuery = "cash",
                        sections = SearchSection.entries.map { SearchSectionUi.Loading(it) },
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("universal_search_content").assertIsDisplayed()
        composeRule.onNodeWithTag("universal_search_section_ledgers_loading").assertIsDisplayed()
    }

    @Test
    fun groupedContentSeeAllAndResultClick() {
        var seeAll: SearchSection? = null
        var clicked: SearchResultRowUi? = null
        composeRule.setContent {
            VentureTheme {
                UniversalSearchScreen(
                    state = contentState(),
                    onEvent = {
                        when (it) {
                            is UniversalSearchEvent.SeeAll -> seeAll = it.section
                            is UniversalSearchEvent.ResultClicked -> clicked = it.row
                            else -> Unit
                        }
                    },
                )
            }
        }
        composeRule.onNodeWithTag("universal_search_section_ledgers").assertIsDisplayed()
        composeRule.onNodeWithTag("universal_search_section_stockitems").assertIsDisplayed()
        composeRule.onNodeWithTag("universal_search_section_vouchers").assertIsDisplayed()
        composeRule.onNodeWithTag("universal_search_voucher_window").assertIsDisplayed()
        composeRule.onNodeWithTag("universal_search_section_ledgers_see_all").performClick()
        composeRule.onNodeWithTag("universal_search_result_vouchers_v1").performClick()
        assertTrue(seeAll == SearchSection.Ledgers)
        assertTrue(clicked?.id == "v1")
    }

    @Test
    fun emptyOfflinePartialAndRetry() {
        var retried = false
        composeRule.setContent {
            VentureTheme {
                UniversalSearchScreen(
                    state = UniversalSearchUiState(
                        query = "x",
                        hasSearched = true,
                        activeQuery = "x",
                        isOnline = false,
                        sections = listOf(
                            SearchSectionUi.Success(
                                SearchSection.Ledgers,
                                listOf(SearchResultRowUi("l1", SearchSection.Ledgers, "Cash", null)),
                                totalItems = 1,
                                showSeeAll = false,
                            ),
                            SearchSectionUi.Failure(
                                SearchSection.StockItems,
                                MasterDataUiError.Timeout("The request timed out."),
                            ),
                            SearchSectionUi.Empty(SearchSection.Vouchers),
                        ),
                    ),
                    onEvent = { if (it is UniversalSearchEvent.RetrySection) retried = true },
                )
            }
        }
        composeRule.onNodeWithTag("universal_search_offline").assertIsDisplayed()
        composeRule.onNodeWithTag("universal_search_partial").assertIsDisplayed()
        composeRule.onNodeWithTag("universal_search_section_stockitems_retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun emptyState() {
        composeRule.setContent {
            VentureTheme {
                UniversalSearchScreen(
                    state = UniversalSearchUiState(
                        query = "zzz",
                        hasSearched = true,
                        activeQuery = "zzz",
                        sections = listOf(
                            SearchSectionUi.Empty(SearchSection.Ledgers),
                            SearchSectionUi.Empty(SearchSection.StockItems),
                            SearchSectionUi.Empty(SearchSection.Vouchers),
                        ),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("universal_search_empty").assertIsDisplayed()
    }

    @Test
    fun clearAction() {
        var cleared = false
        composeRule.setContent {
            VentureTheme {
                UniversalSearchScreen(
                    state = UniversalSearchUiState(query = "cash"),
                    onEvent = { if (it is UniversalSearchEvent.ClearQuery) cleared = true },
                )
            }
        }
        composeRule.onNodeWithTag("universal_search_clear").performClick()
        assertTrue(cleared)
    }

    @Test
    fun queryInputEmitsEvent() {
        val events = mutableListOf<UniversalSearchEvent>()
        composeRule.setContent {
            VentureTheme {
                UniversalSearchScreen(
                    state = UniversalSearchUiState(),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeRule.onNodeWithTag("universal_search_field").performTextInput("ab")
        assertTrue(events.any { it is UniversalSearchEvent.QueryChanged })
    }

    private fun contentState() = UniversalSearchUiState(
        query = "cash",
        hasSearched = true,
        activeQuery = "cash",
        voucherDateFrom = "2026-06-27",
        voucherDateTo = "2026-07-27",
        sections = listOf(
            SearchSectionUi.Success(
                section = SearchSection.Ledgers,
                rows = listOf(SearchResultRowUi("l1", SearchSection.Ledgers, "Cash", "Assets")),
                totalItems = 12,
                showSeeAll = true,
            ),
            SearchSectionUi.Success(
                section = SearchSection.StockItems,
                rows = listOf(SearchResultRowUi("s1", SearchSection.StockItems, "Widget", "Nos")),
                totalItems = 1,
                showSeeAll = false,
            ),
            SearchSectionUi.Success(
                section = SearchSection.Vouchers,
                rows = listOf(SearchResultRowUi("v1", SearchSection.Vouchers, "Sales · S-1", "2026-07-20")),
                totalItems = 1,
                showSeeAll = false,
            ),
        ),
    )
}
