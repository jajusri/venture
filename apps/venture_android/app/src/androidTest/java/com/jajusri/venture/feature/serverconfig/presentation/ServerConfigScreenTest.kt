package com.jajusri.venture.feature.serverconfig.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorHealth
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorReadiness
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorServiceStatus
import com.jajusri.venture.feature.serverconfig.domain.validation.ConnectorUrlValidator
import com.jajusri.venture.feature.serverconfig.domain.validation.toUserMessage
import com.jajusri.venture.ui.theme.VentureTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ServerConfigScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun invalidUrlShowsValidationError() {
        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    ServerConfigUiState(
                        urlInput = "http://10.0.2.2:8080/",
                        savedUrl = "http://10.0.2.2:8080/",
                    ),
                )
            }
            VentureTheme {
                ServerConfigScreen(
                    state = state,
                    onEvent = { event ->
                        if (event is ServerConfigEvent.UrlChanged) {
                            val validation = ConnectorUrlValidator.validate(event.value)
                            state = state.copy(
                                urlInput = event.value,
                                urlValidationError = when (validation) {
                                    is ConnectorUrlValidator.Result.Valid -> null
                                    is ConnectorUrlValidator.Result.Invalid ->
                                        validation.reason.toUserMessage()
                                },
                            )
                        }
                    },
                )
            }
        }
        composeRule.onNodeWithTag("url_input").performTextClearance()
        composeRule.onNodeWithTag("url_input").performTextInput("ftp://bad")
        // url_validation_error lives in OutlinedTextField's supportingText slot; the field merges
        // descendants for accessibility (announced as one unit: label + value + error), so the
        // default merged-tree query can't resolve the error Text's own bounds correctly.
        composeRule.onNodeWithTag("url_validation_error", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun savingUrlShowsFeedback() {
        val state = ServerConfigUiState(
            urlInput = "http://10.0.2.2:8080/",
            savedUrl = "http://10.0.2.2:8080/",
            saveFeedback = "Connector URL saved.",
        )
        composeRule.setContent {
            VentureTheme {
                ServerConfigScreen(state = state, onEvent = {})
            }
        }
        composeRule.onNodeWithTag("save_feedback").assertIsDisplayed()
        composeRule.onNodeWithTag("saved_url").assertIsDisplayed()
    }

    @Test
    fun loadingStateDisablesActions() {
        val state = ServerConfigUiState(
            urlInput = "http://10.0.2.2:8080/",
            savedUrl = "http://10.0.2.2:8080/",
            isTesting = true,
            connection = ConnectionUiState.Loading,
        )
        composeRule.setContent {
            VentureTheme {
                ServerConfigScreen(state = state, onEvent = {})
            }
        }
        composeRule.onNodeWithTag("connection_loading").assertIsDisplayed()
        composeRule.onNodeWithTag("save_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("test_button").assertIsNotEnabled()
    }

    @Test
    fun successfulHealthResultIsShown() {
        val state = ServerConfigUiState(
            urlInput = "http://10.0.2.2:8080/",
            savedUrl = "http://10.0.2.2:8080/",
            connection = ConnectionUiState.Success(sampleProbe()),
        )
        composeRule.setContent {
            VentureTheme {
                ServerConfigScreen(state = state, onEvent = {})
            }
        }
        composeRule.onNodeWithTag("connection_success").assertIsDisplayed()
        // checked_at is the last field in HealthResultContent, on a screen with its own
        // verticalScroll -- can sit below the initially-visible area.
        composeRule.onNodeWithTag("checked_at").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun errorStateShowsRetry() {
        var retried = false
        val state = ServerConfigUiState(
            urlInput = "http://10.0.2.2:8080/",
            savedUrl = "http://10.0.2.2:8080/",
            connection = ConnectionUiState.Error(
                kind = ConnectionErrorKind.Timeout,
                message = "The request timed out.",
            ),
        )
        composeRule.setContent {
            VentureTheme {
                ServerConfigScreen(
                    state = state,
                    onEvent = { if (it is ServerConfigEvent.RetryClicked) retried = true },
                )
            }
        }
        composeRule.onNodeWithTag("connection_error").assertIsDisplayed()
        composeRule.onNodeWithTag("connection_error_kind").assertIsDisplayed()
        composeRule.onNodeWithTag("retry_button").assertIsEnabled().performClick()
        assertTrue(retried)
    }
}

private fun sampleProbe() = ConnectorConnectionProbe(
    health = ConnectorHealth(
        status = "ok",
        schemaVersion = "1.0.0",
        connectorVersion = "0.4.0",
        tallyReachable = true,
        readOnly = true,
        bindHost = "127.0.0.1",
        bindPort = 8080,
        networkExposure = "loopback",
        networkExposureWarning = null,
        networkPolicySatisfied = true,
        authenticatedLanAccessEnabled = false,
        services = listOf(ConnectorServiceStatus("ApiServer", true, true)),
        startupCorrelationId = null,
        repositoryAvailable = true,
        databaseAccessible = true,
    ),
    readiness = ConnectorReadiness(
        status = "ready",
        repositoryAvailable = true,
        databaseAccessible = true,
        voucherSynchronizationComposed = true,
        voucherApplicationComposed = true,
        httpStatus = 200,
    ),
    checkedAtEpochMillis = 1_700_000_000_000L,
)
