package com.budcom.android.feature.company.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
