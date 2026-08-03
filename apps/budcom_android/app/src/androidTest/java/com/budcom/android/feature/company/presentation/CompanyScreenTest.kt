package com.budcom.android.feature.company.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.budcom.android.feature.company.domain.model.ConnectorCompany
import com.budcom.android.ui.theme.BudcomTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompanyScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingStateIsShown() {
        composeRule.setContent {
            BudcomTheme {
                CompanyScreen(
                    state = CompanyUiState(isLoading = true),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("company_loading").assertIsDisplayed()
    }

    @Test
    fun emptyStateIsShown() {
        composeRule.setContent {
            BudcomTheme {
                CompanyScreen(
                    state = CompanyUiState(
                        isLoading = false,
                        companies = emptyList(),
                        filteredCompanies = emptyList(),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("company_empty").assertIsDisplayed()
    }

    @Test
    fun populatedListIsShown() {
        composeRule.setContent {
            BudcomTheme {
                CompanyScreen(
                    state = sampleState(),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("company_list").assertIsDisplayed()
        composeRule.onNodeWithTag("company_card_estimation").assertIsDisplayed()
    }

    @Test
    fun selectingCompanySendsEvent() {
        var selected: String? = null
        composeRule.setContent {
            BudcomTheme {
                CompanyScreen(
                    state = sampleState(),
                    onEvent = { event ->
                        if (event is CompanyEvent.SelectCompany) selected = event.companyId
                    },
                )
            }
        }
        composeRule.onNodeWithTag("company_card_estimation").performClick()
        assertEquals("estimation", selected)
    }

    /**
     * [CONSTRAINED_HEIGHT] is not an arbitrary "shrink until it passes" number. On this
     * device/theme (Material3 BOM 2025.02.00), the chrome above the list is:
     * status bar inset (~40dp, real device inset — TopAppBar's default `windowInsets`
     * includes it) + TopAppBar content (64dp) + Column vertical padding (2x12dp) + search
     * field (~56dp) + item spacing (10dp) = ~194dp, before a single list item is drawn. One
     * company card's minimum content (14dp Row padding x2 + a 3-line text block) is
     * ~88-100dp. [CONSTRAINED_HEIGHT] gives ~194dp chrome + ~150dp list — enough to prove the
     * first card renders at full height and a second card requires an explicit scroll to
     * reach, without the generous slack that would make the test unable to catch a real list
     * collapse. 240dp (the original value) left under 46dp for the list — less than half of
     * one card's minimum height — which is why even the *first* card failed to display; that
     * was the viewport being unrealistic, not the list collapsing due to a code defect.
     */
    @Test
    fun companyListScrollsToAndSelectsSecondItem() {
        var selected: String? = null
        composeRule.setContent {
            BudcomTheme {
                Box(modifier = Modifier.size(width = CONSTRAINED_WIDTH, height = CONSTRAINED_HEIGHT)) {
                    CompanyScreen(
                        state = twoCompanyState(),
                        onEvent = { event ->
                            if (event is CompanyEvent.SelectCompany) selected = event.companyId
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("company_card_budcom-test-01").assertIsDisplayed()
        composeRule.onNodeWithTag("company_list").performScrollToIndex(1)
        composeRule.onNodeWithText("ESTIMATION").assertIsDisplayed()
        composeRule.onNodeWithTag("company_card_estimation").performClick()

        assertEquals("estimation", selected)
    }

    @Test
    fun constrainedHeightListRetainsNonZeroUsableArea() {
        composeRule.setContent {
            BudcomTheme {
                Box(modifier = Modifier.size(width = CONSTRAINED_WIDTH, height = CONSTRAINED_HEIGHT)) {
                    CompanyScreen(state = twoCompanyState(), onEvent = {})
                }
            }
        }

        // Strict enough to catch a real collapse: a list squeezed to a few dp (or 0) fails
        // this, while the layout fix (weight(1f) instead of a non-weighted fillMaxSize)
        // keeps it well above one card's minimum height.
        composeRule.onNodeWithTag("company_list").assertHeightIsAtLeast(60.dp)
    }

    @Test
    fun searchRemainsAccessibleUnderConstrainedHeight() {
        composeRule.setContent {
            BudcomTheme {
                Box(modifier = Modifier.size(width = CONSTRAINED_WIDTH, height = CONSTRAINED_HEIGHT)) {
                    CompanyScreen(state = twoCompanyState(), onEvent = {})
                }
            }
        }
        composeRule.onNodeWithTag("company_search").assertIsDisplayed()
    }

    @Test
    fun emptyStateRendersUnderConstrainedHeight() {
        composeRule.setContent {
            BudcomTheme {
                Box(modifier = Modifier.size(width = CONSTRAINED_WIDTH, height = CONSTRAINED_HEIGHT)) {
                    CompanyScreen(
                        state = CompanyUiState(isLoading = false, companies = emptyList(), filteredCompanies = emptyList()),
                        onEvent = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("company_empty").assertIsDisplayed()
    }

    @Test
    fun loadingStateRendersUnderConstrainedHeight() {
        composeRule.setContent {
            BudcomTheme {
                Box(modifier = Modifier.size(width = CONSTRAINED_WIDTH, height = CONSTRAINED_HEIGHT)) {
                    CompanyScreen(state = CompanyUiState(isLoading = true), onEvent = {})
                }
            }
        }
        composeRule.onNodeWithTag("company_loading").assertIsDisplayed()
    }

    @Test
    fun errorStateRendersUnderConstrainedHeightAndRetryStaysReachable() {
        var retried = false
        composeRule.setContent {
            BudcomTheme {
                Box(modifier = Modifier.size(width = CONSTRAINED_WIDTH, height = CONSTRAINED_HEIGHT)) {
                    CompanyScreen(
                        state = CompanyUiState(isLoading = false, error = CompanyUiError.Timeout("The request timed out.")),
                        onEvent = { event -> if (event is CompanyEvent.Retry) retried = true },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("company_error").assertIsDisplayed()
        composeRule.onNodeWithTag("company_retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun noCompanyIsAutoSelectedMerelyByAppearingFirst() {
        var selectEventCount = 0
        composeRule.setContent {
            BudcomTheme {
                CompanyScreen(
                    state = twoCompanyState().copy(selectedCompanyId = null, selectedCompanyName = null),
                    onEvent = { event -> if (event is CompanyEvent.SelectCompany) selectEventCount++ },
                )
            }
        }

        composeRule.onNodeWithTag("company_card_budcom-test-01").assertIsDisplayed()
        // No card renders the "Selected company" label, and onEvent never fires, purely from
        // initial composition — selection only ever happens from an explicit user click.
        composeRule.onAllNodesWithText("Selected company").assertCountEquals(0)
        assertEquals(0, selectEventCount)
    }

    @Test
    fun retryActionSendsEvent() {
        var retried = false
        composeRule.setContent {
            BudcomTheme {
                CompanyScreen(
                    state = CompanyUiState(
                        isLoading = false,
                        error = CompanyUiError.Timeout("The request timed out."),
                    ),
                    onEvent = { event ->
                        if (event is CompanyEvent.Retry) retried = true
                    },
                )
            }
        }
        composeRule.onNodeWithTag("company_retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun offlineStateIsShown() {
        composeRule.setContent {
            BudcomTheme {
                CompanyScreen(
                    state = CompanyUiState(
                        isLoading = false,
                        error = CompanyUiError.Offline("No network connection."),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("company_error").assertIsDisplayed()
    }

    private companion object {
        val CONSTRAINED_WIDTH = 360.dp
        val CONSTRAINED_HEIGHT = 340.dp
    }
}

private fun sampleState() = CompanyUiState(
    isLoading = false,
    companies = listOf(
        ConnectorCompany("estimation", "ESTIMATION", null, null, "INR"),
    ),
    filteredCompanies = listOf(
        ConnectorCompany("estimation", "ESTIMATION", null, null, "INR"),
    ),
    selectedCompanyId = "estimation",
)

private fun twoCompanyState() = CompanyUiState(
    isLoading = false,
    companies = listOf(
        ConnectorCompany("budcom-test-01", "Budcom-Test-01", null, null, "INR"),
        ConnectorCompany("estimation", "ESTIMATION", null, null, "INR"),
    ),
    filteredCompanies = listOf(
        ConnectorCompany("budcom-test-01", "Budcom-Test-01", null, null, "INR"),
        ConnectorCompany("estimation", "ESTIMATION", null, null, "INR"),
    ),
    selectedCompanyId = "budcom-test-01",
    selectedCompanyName = "Budcom-Test-01",
)
