package com.budcom.android.feature.connect.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProspectCreateRoute(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: ProspectCreateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProspectCreateEffect.Created -> onCreated(effect.partyId)
            }
        }
    }
    ProspectCreateScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProspectCreateScreen(
    state: ProspectCreateUiState,
    onEvent: (ProspectCreateEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("prospect_create_screen"),
        topBar = {
            TopAppBar(
                title = { Text("New prospect") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("prospect_create_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("No accounting information is required — a prospect can be created with just a name.")

            OutlinedTextField(
                value = state.displayName,
                onValueChange = { onEvent(ProspectCreateEvent.DisplayNameChanged(it)) },
                label = { Text("Business or person name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("prospect_name"),
            )
            OutlinedTextField(
                value = state.phone,
                onValueChange = { onEvent(ProspectCreateEvent.PhoneChanged(it)) },
                label = { Text("Phone (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("prospect_phone"),
            )
            OutlinedTextField(
                value = state.email,
                onValueChange = { onEvent(ProspectCreateEvent.EmailChanged(it)) },
                label = { Text("Email (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("prospect_email"),
            )
            OutlinedTextField(
                value = state.addressLine1,
                onValueChange = { onEvent(ProspectCreateEvent.AddressLine1Changed(it)) },
                label = { Text("Address (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("prospect_address"),
            )
            OutlinedTextField(
                value = state.addressCity,
                onValueChange = { onEvent(ProspectCreateEvent.AddressCityChanged(it)) },
                label = { Text("City (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("prospect_city"),
            )
            OutlinedTextField(
                value = state.addressState,
                onValueChange = { onEvent(ProspectCreateEvent.AddressStateChanged(it)) },
                label = { Text("State (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("prospect_state"),
            )
            OutlinedTextField(
                value = state.addressPincode,
                onValueChange = { onEvent(ProspectCreateEvent.AddressPincodeChanged(it)) },
                label = { Text("Pincode (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("prospect_pincode"),
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = { onEvent(ProspectCreateEvent.NoteChanged(it)) },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth().testTag("prospect_note"),
            )

            state.error?.let { Text(it) }

            Button(
                onClick = { onEvent(ProspectCreateEvent.Save) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().testTag("prospect_save"),
            ) {
                Text(if (state.isSaving) "Saving…" else "Save prospect")
            }
        }
    }
}
