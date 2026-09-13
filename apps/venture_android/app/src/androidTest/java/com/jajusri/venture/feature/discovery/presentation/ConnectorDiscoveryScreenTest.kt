package com.jajusri.venture.feature.discovery.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jajusri.venture.core.discovery.DiscoveredConnector
import com.jajusri.venture.ui.theme.VentureTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConnectorDiscoveryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val connector = DiscoveredConnector(
        connectorId = "9c98ff3c-3b1c-4429-a1a9-4055ef4c95e4",
        name = "Front Desk",
        host = "192.168.29.34",
        port = 8080,
        apiVersion = "1.0.0",
        authRequired = false,
    )

    @Test
    fun discoveringStateShowsSpinner() {
        composeRule.setContent {
            VentureTheme {
                ConnectorDiscoveryScreen(
                    state = ConnectorDiscoveryUiState(phase = ConnectorDiscoveryPhase.Discovering),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("discovery_discovering").assertIsDisplayed()
    }

    @Test
    fun emptyStateOffersRetryAndManualFallback_neverAutoRetriesSilently() {
        var retried = false
        var openedManualConfig = false
        composeRule.setContent {
            VentureTheme {
                ConnectorDiscoveryScreen(
                    state = ConnectorDiscoveryUiState(phase = ConnectorDiscoveryPhase.Empty),
                    onEvent = { event ->
                        when (event) {
                            ConnectorDiscoveryEvent.Retry -> retried = true
                            ConnectorDiscoveryEvent.OpenManualConfig -> openedManualConfig = true
                            else -> Unit
                        }
                    },
                )
            }
        }
        composeRule.onNodeWithTag("discovery_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("discovery_retry_button").assertIsDisplayed().performClick()
        assertTrue(retried)
        composeRule.onNodeWithTag("discovery_manual_config_button").assertIsDisplayed().performClick()
        assertTrue(openedManualConfig)
    }

    @Test
    fun foundStateListsDiscoveredConnector_selectingItEmitsSelectNotPair() {
        var selected: DiscoveredConnector? = null
        composeRule.setContent {
            VentureTheme {
                ConnectorDiscoveryScreen(
                    state = ConnectorDiscoveryUiState(
                        phase = ConnectorDiscoveryPhase.Found,
                        discovered = listOf(connector),
                    ),
                    onEvent = { event -> if (event is ConnectorDiscoveryEvent.Select) selected = event.candidate },
                )
            }
        }
        composeRule.onNodeWithTag("discovery_found").assertIsDisplayed()
        composeRule.onNodeWithTag("discovered_connector_${connector.connectorId}")
            .assertIsDisplayed()
            .performClick()
        assertEquals(connector, selected)
    }

    @Test
    fun confirmingStateRequiresExplicitConfirmBeforePairing() {
        var confirmed = false
        composeRule.setContent {
            VentureTheme {
                ConnectorDiscoveryScreen(
                    state = ConnectorDiscoveryUiState(
                        phase = ConnectorDiscoveryPhase.Confirming,
                        selected = connector,
                    ),
                    onEvent = { event -> if (event is ConnectorDiscoveryEvent.ConfirmPairing) confirmed = true },
                )
            }
        }
        composeRule.onNodeWithTag("discovery_confirming").assertIsDisplayed()
        assertTrue(!confirmed)
        composeRule.onNodeWithTag("discovery_confirm_button").assertIsDisplayed().performClick()
        assertTrue(confirmed)
    }

    @Test
    fun pairingStateShowsSpinner() {
        composeRule.setContent {
            VentureTheme {
                ConnectorDiscoveryScreen(
                    state = ConnectorDiscoveryUiState(phase = ConnectorDiscoveryPhase.Pairing),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("discovery_pairing").assertIsDisplayed()
    }
}
