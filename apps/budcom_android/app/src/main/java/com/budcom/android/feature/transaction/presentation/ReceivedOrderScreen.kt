package com.budcom.android.feature.transaction.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ReceivedOrderRoute(viewModel: ReceivedOrderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReceivedOrderScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivedOrderScreen(state: ReceivedOrderUiState, onEvent: (ReceivedOrderEvent) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Received order") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.testTag("received_order_loading"))
            } else {
                Text("Order ${state.orderId.orEmpty()}", modifier = Modifier.testTag("received_order_id"))
                Text("Version ${state.orderVersion ?: 1}", modifier = Modifier.testTag("received_order_version"))
                Text(
                    "Status: ${state.statusLabel.orEmpty()}",
                    modifier = Modifier.testTag("received_order_status"),
                )
                if (state.canConfirm) {
                    Button(
                        onClick = { onEvent(ReceivedOrderEvent.ConfirmOrder) },
                        modifier = Modifier.fillMaxWidth().testTag("received_order_confirm"),
                    ) { Text("Confirm Order") }
                }
            }
            state.message?.let { Text(it, modifier = Modifier.testTag("received_order_message")) }
        }
    }
}
