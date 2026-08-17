package com.budcom.android.feature.connect.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.feature.masterdata.presentation.MasterDataErrorBlock
import com.budcom.android.feature.masterdata.presentation.MasterDataLoadingIndicator
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.PartyExportEvent
import com.budcom.android.feature.party.domain.model.TallyFieldExportCandidate

@Composable
fun PartyXmlExportRoute(
    viewModel: PartyXmlExportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/xml")) { uri ->
        viewModel.onEvent(PartyXmlExportEvent.SaveDestinationSelected(uri))
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PartyXmlExportEffect.CreateXmlDocument -> saveLauncher.launch(effect.suggestedFilename)
            }
        }
    }
    PartyXmlExportScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyXmlExportScreen(
    state: PartyXmlExportUiState,
    onEvent: (PartyXmlExportEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("party_xml_export_screen"),
        topBar = { TopAppBar(title = { Text("Export to Tally") }) },
    ) { innerPadding ->
        when {
            state.isLoading -> MasterDataLoadingIndicator(modifier = Modifier.padding(innerPadding), testTag = "party_xml_export_loading")
            state.error != null -> MasterDataErrorBlock(
                error = state.error,
                onRetry = { onEvent(PartyXmlExportEvent.Retry) },
                modifier = Modifier.padding(innerPadding),
                errorTestTag = "party_xml_export_error",
                retryTestTag = "party_xml_export_retry",
            )
            else -> PartyXmlExportContent(state, onEvent, Modifier.padding(innerPadding))
        }
    }

    state.notice?.let { message ->
        AlertDialog(
            onDismissRequest = { onEvent(PartyXmlExportEvent.DismissNotice) },
            confirmButton = {
                TextButton(onClick = { onEvent(PartyXmlExportEvent.DismissNotice) }, modifier = Modifier.testTag("party_xml_export_notice_dismiss")) {
                    Text("OK")
                }
            },
            text = { Text(message, modifier = Modifier.testTag("party_xml_export_notice_message")) },
            modifier = Modifier.testTag("party_xml_export_notice_dialog"),
        )
    }
}

@Composable
private fun PartyXmlExportContent(state: PartyXmlExportUiState, onEvent: (PartyXmlExportEvent) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("party_xml_export_content"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Review the fields below, then generate a Tally-compatible XML file to import manually. " +
                "BUDCOM never writes to Tally directly.",
            style = MaterialTheme.typography.bodyMedium,
        )

        state.candidates.forEach { candidate ->
            FieldCandidateRow(
                candidate = candidate,
                isSelected = candidate.fieldName in state.selectedFieldNames,
                onToggle = { onEvent(PartyXmlExportEvent.FieldSelectionToggled(candidate.fieldName)) },
            )
        }

        Button(
            onClick = { onEvent(PartyXmlExportEvent.GenerateTapped) },
            enabled = state.canGenerate,
            modifier = Modifier.fillMaxWidth().testTag("party_xml_export_generate"),
        ) {
            Text(if (state.isGenerating) "Generating…" else "Generate Tally export")
        }

        OutlinedButton(
            onClick = { onEvent(PartyXmlExportEvent.CheckTallyTapped) },
            enabled = !state.isSyncing && state.candidates.any { it.state == FieldProvenanceState.Exported },
            modifier = Modifier.fillMaxWidth().testTag("party_xml_export_check_tally"),
        ) {
            Text(if (state.isSyncing) "Checking Tally…" else "Check Tally")
        }

        if (state.history.isNotEmpty()) {
            Text("Export history", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.testTag("party_xml_export_history_list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.history.forEach { event -> ExportHistoryRow(event) }
            }
        }
    }
}

@Composable
private fun FieldCandidateRow(candidate: TallyFieldExportCandidate, isSelected: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag("party_xml_export_field_${candidate.fieldName}")) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                modifier = Modifier
                    .testTag("party_xml_export_field_${candidate.fieldName}_checkbox")
                    .semantics { contentDescription = "Include ${candidate.label} in the Tally export" },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(candidate.label, style = MaterialTheme.typography.labelMedium)
                Text(candidate.budcomValue ?: "Not set", style = MaterialTheme.typography.bodyMedium)
                Text(
                    stateLabel(candidate.state),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (candidate.state == FieldProvenanceState.Conflict) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.testTag("party_xml_export_field_${candidate.fieldName}_state"),
                )
                candidate.tallyValue?.let { Text("Tally: $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun ExportHistoryRow(event: PartyExportEvent) {
    Card(modifier = Modifier.fillMaxWidth().testTag("party_xml_export_history_${event.exportId}")) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(event.outputFileName, style = MaterialTheme.typography.bodyMedium)
            Text(event.fieldNames.joinToString(", "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun stateLabel(state: FieldProvenanceState): String = when (state) {
    FieldProvenanceState.ConfirmedFromTally -> "Confirmed from Tally"
    FieldProvenanceState.BudcomOnlyPending -> "Pending in BUDCOM"
    FieldProvenanceState.ExportReady -> "Ready to export"
    FieldProvenanceState.Exported -> "Exported, awaiting Tally"
    FieldProvenanceState.Conflict -> "Needs review"
    FieldProvenanceState.EmptyUnknown -> "Not set"
}
