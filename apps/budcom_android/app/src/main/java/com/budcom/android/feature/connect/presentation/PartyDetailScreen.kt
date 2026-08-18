package com.budcom.android.feature.connect.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.feature.masterdata.presentation.MasterDataErrorBlock
import com.budcom.android.feature.masterdata.presentation.MasterDataLoadingIndicator
import com.budcom.android.feature.party.domain.model.IssueStatus
import com.budcom.android.feature.party.domain.model.NoteType
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyExportEvent
import com.budcom.android.feature.party.domain.model.PartyNote
import com.budcom.android.feature.party.domain.model.TallyExportFieldMapping
import com.budcom.android.feature.party.domain.model.TimelineEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PartyDetailRoute(
    onOpenLedgerStatement: (String) -> Unit,
    onOpenVouchers: (String) -> Unit,
    onOpenVoucherDetails: (String) -> Unit,
    onOpenXmlExport: () -> Unit,
    viewModel: PartyDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PartyDetailEffect.OpenLedgerStatement -> onOpenLedgerStatement(effect.ledgerId)
                is PartyDetailEffect.OpenVouchers -> onOpenVouchers(effect.query)
                is PartyDetailEffect.OpenVoucherDetails -> onOpenVoucherDetails(effect.voucherId)
                is PartyDetailEffect.LaunchCall ->
                    runCatching { context.startActivity(com.budcom.android.feature.connect.presentation.ConnectContactActions.callIntent(effect.phoneE164)) }
                is PartyDetailEffect.LaunchWhatsApp ->
                    runCatching { context.startActivity(com.budcom.android.feature.connect.presentation.ConnectContactActions.whatsAppChatIntent(effect.phoneE164)) }
                PartyDetailEffect.OpenXmlExport -> onOpenXmlExport()
            }
        }
    }
    PartyDetailScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyDetailScreen(
    state: PartyDetailUiState,
    onEvent: (PartyDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("party_detail_screen"),
        topBar = {
            TopAppBar(title = { Text(state.party?.displayName ?: "Party") })
        },
    ) { innerPadding ->
        when {
            state.isLoading && state.party == null -> MasterDataLoadingIndicator(
                modifier = Modifier.padding(innerPadding),
                testTag = "party_detail_loading",
            )
            state.error != null && state.party == null -> MasterDataErrorBlock(
                error = state.error,
                onRetry = { onEvent(PartyDetailEvent.Retry) },
                modifier = Modifier.padding(innerPadding),
                errorTestTag = "party_detail_error",
                retryTestTag = "party_detail_retry",
            )
            state.party != null -> PartyDetailContent(state, onEvent, Modifier.padding(innerPadding))
        }
    }

    when (val dialog = state.activeDialog) {
        is PartyDetailDialog.EditField -> EditFieldDialog(dialog, onEvent)
        is PartyDetailDialog.ContactPersonEditor -> ContactPersonEditorDialog(dialog, onEvent)
        PartyDetailDialog.AddTag -> AddTagDialog(state, onEvent)
        is PartyDetailDialog.NoteEditor -> NoteEditorDialog(dialog, onEvent)
        null -> Unit
    }

    state.notice?.let { message ->
        AlertDialog(
            onDismissRequest = { onEvent(PartyDetailEvent.DismissNotice) },
            confirmButton = {
                TextButton(onClick = { onEvent(PartyDetailEvent.DismissNotice) }, modifier = Modifier.testTag("party_detail_notice_dismiss")) {
                    Text("OK")
                }
            },
            text = { Text(message, modifier = Modifier.testTag("party_detail_notice_message")) },
            modifier = Modifier.testTag("party_detail_notice_dialog"),
        )
    }
}

@Composable
private fun PartyDetailContent(state: PartyDetailUiState, onEvent: (PartyDetailEvent) -> Unit, modifier: Modifier) {
    val party = state.party ?: return
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("party_detail_content"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // A. Business / Party identity
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(party.displayName, style = MaterialTheme.typography.headlineSmall)
                Text(party.classification.name, style = MaterialTheme.typography.bodySmall)
            }
            state.balanceLabel?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { onEvent(PartyDetailEvent.CallTapped(effectivePhoneE164(state))) },
                label = { Text("Call") },
                modifier = Modifier.testTag("party_detail_call"),
            )
            AssistChip(
                onClick = { onEvent(PartyDetailEvent.WhatsAppTapped(effectivePhoneE164(state))) },
                label = { Text("WhatsApp") },
                modifier = Modifier.testTag("party_detail_whatsapp"),
            )
        }

        // D. Accounting context / deep links — only when a source link exists.
        if (state.hasAccountingLink) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onEvent(PartyDetailEvent.ViewLedgerTapped(state.sourceLink?.externalEntityId)) },
                    modifier = Modifier.testTag("party_detail_view_ledger"),
                ) { Text("View Ledger") }
                OutlinedButton(
                    onClick = { onEvent(PartyDetailEvent.ViewVouchersTapped(party.displayName)) },
                    modifier = Modifier.testTag("party_detail_view_vouchers"),
                ) { Text("View Vouchers") }
                OutlinedButton(
                    onClick = { onEvent(PartyDetailEvent.ExportToTallyTapped) },
                    modifier = Modifier.testTag("party_detail_export_to_tally"),
                ) { Text("Export to Tally") }
            }
        }

        // B/C. Tally-confirmed + BUDCOM-only contact fields, with provenance.
        SectionHeader("Contact details")
        state.fieldRows.forEach { row ->
            FieldRow(row, onClick = { onEvent(PartyDetailEvent.EditFieldTapped(row.fieldName, row.label)) })
        }

        // Tags
        SectionHeader("Tags")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.tags.forEach { tag ->
                AssistChip(
                    onClick = { onEvent(PartyDetailEvent.RemoveTagTapped(tag.tagId)) },
                    label = { Text(tag.name) },
                    modifier = Modifier.testTag("party_detail_tag_${tag.tagId}"),
                )
            }
            TextButton(onClick = { onEvent(PartyDetailEvent.AddTagTapped) }, modifier = Modifier.testTag("party_detail_add_tag")) {
                Text("Add tag")
            }
        }

        // Contact persons
        SectionHeader("Contact persons")
        state.contactPersons.forEach { contact ->
            ContactPersonRow(contact, onEvent)
        }
        TextButton(onClick = { onEvent(PartyDetailEvent.AddContactTapped) }, modifier = Modifier.testTag("party_detail_add_contact")) {
            Text("Add contact person")
        }

        // E. Issues (MVP-1.2-C) — open issues prominent but compact, resolved issues collapsed and
        // de-emphasized. Never a second chronological history: tapping an issue filters the same
        // Relationship Timeline below, it does not open a separate list (architecture §10).
        IssuesSection(state, onEvent)

        // F. Relationship Timeline (PDL-014: the unified historical presentation for this Party —
        // notes, Tally-export events, and issue-lifecycle events merged into one chronological
        // feed, never two competing histories).
        SectionHeader("Relationship Timeline")
        state.selectedIssueFilter?.let { filtered ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Showing: ${filtered.issue.title}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("party_detail_timeline_filter_banner"),
                )
                TextButton(
                    onClick = { onEvent(PartyDetailEvent.ClearIssueFilterTapped) },
                    modifier = Modifier.testTag("party_detail_timeline_clear_filter"),
                ) { Text("Show full timeline") }
            }
        }
        if (state.timeline.isEmpty()) {
            Text(
                "No activity yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("party_detail_timeline_empty"),
            )
        }
        state.timeline.forEach { entry -> TimelineRow(entry, onEvent) }
        if (state.timelineCanLoadMore) {
            TextButton(
                onClick = { onEvent(PartyDetailEvent.LoadMoreTimeline) },
                modifier = Modifier.testTag("party_detail_load_more_timeline"),
            ) { Text(if (state.isLoadingMoreTimeline) "Loading…" else "Load more") }
        }
        TextButton(onClick = { onEvent(PartyDetailEvent.AddNoteTapped) }, modifier = Modifier.testTag("party_detail_add_note")) {
            Text("Add note")
        }
    }
}

private fun effectivePhoneE164(state: PartyDetailUiState): String? =
    com.budcom.android.core.util.PhoneNumberNormalizer.normalizeIndianMobile(state.party?.primaryPhone)

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun FieldRow(row: PartyFieldRowUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("party_detail_field_${row.fieldName}"),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.label, style = MaterialTheme.typography.labelMedium)
            Text(
                row.value ?: "Not set",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.provenanceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = if (row.isConflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("party_detail_field_${row.fieldName}_provenance"),
            )
        }
        TextButton(onClick = onClick, modifier = Modifier.testTag("party_detail_field_${row.fieldName}_edit")) { Text("Edit") }
    }
}

@Composable
private fun ContactPersonRow(contact: PartyContactPerson, onEvent: (PartyDetailEvent) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag("party_detail_contact_${contact.contactPersonId}")) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(if (contact.isPrimary) "${contact.name} · Primary" else contact.name)
                contact.designation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                contact.mobile?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Row {
                TextButton(
                    onClick = { onEvent(PartyDetailEvent.EditContactTapped(contact.contactPersonId)) },
                    modifier = Modifier.testTag("party_detail_contact_${contact.contactPersonId}_edit"),
                ) { Text("Edit") }
                TextButton(
                    onClick = { onEvent(PartyDetailEvent.DeleteContactTapped(contact.contactPersonId)) },
                    modifier = Modifier.testTag("party_detail_contact_${contact.contactPersonId}_delete"),
                ) { Text("Delete") }
            }
        }
    }
}

/** MVP-1.2-C Issues section — omitted entirely when the Party has never had an issue (nothing to
 * show, no perpetual empty clutter). Open issues shown first, compactly (a count is enough at a
 * glance, full cards on tap); resolved issues collapse into their own secondary, de-emphasized
 * disclosure (architecture §10). */
@Composable
private fun IssuesSection(state: PartyDetailUiState, onEvent: (PartyDetailEvent) -> Unit) {
    if (state.issues.isEmpty()) return
    SectionHeader("Issues")
    val openCount = state.openIssues.size
    TextButton(
        onClick = { onEvent(PartyDetailEvent.ToggleIssuesExpanded) },
        modifier = Modifier.testTag("party_detail_issues_toggle"),
    ) { Text(if (openCount == 0) "No open issues" else "$openCount open ${if (openCount == 1) "issue" else "issues"}") }

    if (state.issuesExpanded) {
        state.openIssues.forEach { card -> IssueCard(card, state.selectedIssueFilterId, onEvent) }

        if (state.resolvedIssues.isNotEmpty()) {
            TextButton(
                onClick = { onEvent(PartyDetailEvent.ToggleResolvedIssuesExpanded) },
                modifier = Modifier.testTag("party_detail_resolved_issues_toggle"),
            ) { Text("${state.resolvedIssues.size} resolved ${if (state.resolvedIssues.size == 1) "issue" else "issues"}") }

            if (state.resolvedIssuesExpanded) {
                state.resolvedIssues.forEach { card -> IssueCard(card, state.selectedIssueFilterId, onEvent) }
            }
        }
    }
}

@Composable
private fun IssueCard(card: IssueCardUi, selectedFilterId: String?, onEvent: (PartyDetailEvent) -> Unit) {
    val issue = card.issue
    Card(modifier = Modifier.fillMaxWidth().testTag("party_detail_issue_${issue.issueId}")) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(issue.title, modifier = Modifier.weight(1f))
                Text(
                    issue.status.toUiLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.testTag("party_detail_issue_${issue.issueId}_status"),
                )
            }
            Text(
                "${card.noteCount} ${if (card.noteCount == 1) "note" else "notes"} · ${formatTimelineDate(card.lastActivityAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                val filtered = selectedFilterId == issue.issueId
                TextButton(
                    onClick = { onEvent(PartyDetailEvent.IssueFilterTapped(issue.issueId)) },
                    modifier = Modifier.testTag("party_detail_issue_${issue.issueId}_filter"),
                ) { Text(if (filtered) "✓ Filtered" else "View in Timeline") }
                if (issue.status == IssueStatus.Open) {
                    TextButton(
                        onClick = { onEvent(PartyDetailEvent.ResolveIssueTapped(issue.issueId)) },
                        modifier = Modifier.testTag("party_detail_issue_${issue.issueId}_resolve"),
                    ) { Text("Resolve") }
                } else {
                    TextButton(
                        onClick = { onEvent(PartyDetailEvent.ReopenIssueTapped(issue.issueId)) },
                        modifier = Modifier.testTag("party_detail_issue_${issue.issueId}_reopen"),
                    ) { Text("Reopen") }
                }
            }
        }
    }
}

/** MVP-1.2-B/C Relationship Timeline dispatch — one row per truthful, locally-sourced event
 * (architecture §10/§16, PDL-014). Reuses [NoteRow] verbatim for note-kind entries rather than a
 * second rendering path. Issue-lifecycle rows are read live from [TimelineEntry.IssueOpenedEvent]/
 * [TimelineEntry.IssueResolvedEvent], which the repository derives directly from `party_issues` —
 * never a separately-stored record — so this can never disagree with the Issues section above. */
@Composable
private fun TimelineRow(entry: TimelineEntry, onEvent: (PartyDetailEvent) -> Unit) {
    when (entry) {
        is TimelineEntry.NoteEvent -> NoteRow(entry.note, onEvent)
        is TimelineEntry.ExportEvent -> ExportEventRow(entry.event)
        is TimelineEntry.IssueOpenedEvent ->
            IssueLifecycleRow(testTag = "party_detail_issue_event_${entry.issueId}_opened", text = "Issue opened: ${entry.title}", timestamp = entry.openedAt)
        is TimelineEntry.IssueResolvedEvent ->
            IssueLifecycleRow(testTag = "party_detail_issue_event_${entry.issueId}_resolved", text = "Issue resolved: ${entry.title}", timestamp = entry.resolvedAt)
    }
}

@Composable
private fun IssueLifecycleRow(testTag: String, text: String, timestamp: Long) {
    Card(modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text)
            Text(formatTimelineDate(timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExportEventRow(event: PartyExportEvent) {
    Card(modifier = Modifier.fillMaxWidth().testTag("party_detail_export_event_${event.exportId}")) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Exported to Tally: ${event.fieldNames.joinToString(", ") { TallyExportFieldMapping.labelFor(it) }}",
                modifier = Modifier.testTag("party_detail_export_event_${event.exportId}_summary"),
            )
            Text(
                formatTimelineDate(event.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val TIMELINE_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

/** Matches [com.budcom.android.feature.voucher.presentation.formatVoucherDate]'s display
 * convention ("dd MMM yyyy"), applied to an epoch-millis Timeline timestamp instead of an
 * ISO date-only string. */
private fun formatTimelineDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(TIMELINE_DATE_FORMATTER)

@Composable
private fun NoteRow(note: PartyNote, onEvent: (PartyDetailEvent) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag("party_detail_note_${note.noteId}")) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(note.body)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    formatTimelineDate(note.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (note.type != NoteType.General) {
                    Text(
                        note.type.toUiLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("party_detail_note_${note.noteId}_type"),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row {
                    note.linkedVoucherId?.let { voucherId ->
                        TextButton(
                            onClick = { onEvent(PartyDetailEvent.LinkedVoucherTapped(voucherId)) },
                            modifier = Modifier.testTag("party_detail_note_${note.noteId}_voucher"),
                        ) { Text("Linked voucher") }
                    }
                    TextButton(
                        onClick = { onEvent(PartyDetailEvent.EditNoteTapped(note.noteId)) },
                        modifier = Modifier.testTag("party_detail_note_${note.noteId}_edit"),
                    ) { Text("Edit") }
                }
                TextButton(
                    onClick = { onEvent(PartyDetailEvent.DeleteNoteTapped(note.noteId)) },
                    modifier = Modifier.testTag("party_detail_note_${note.noteId}_delete"),
                ) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun EditFieldDialog(dialog: PartyDetailDialog.EditField, onEvent: (PartyDetailEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(PartyDetailEvent.DismissDialog) },
        title = { Text("Edit ${dialog.label}") },
        text = {
            OutlinedTextField(
                value = dialog.currentValue,
                onValueChange = { onEvent(PartyDetailEvent.EditFieldValueChanged(it)) },
                modifier = Modifier.fillMaxWidth().testTag("party_detail_edit_field_value"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onEvent(PartyDetailEvent.SaveEditedField) }, modifier = Modifier.testTag("party_detail_edit_field_save")) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(PartyDetailEvent.DismissDialog) }) { Text("Cancel") }
        },
        modifier = Modifier.testTag("party_detail_edit_field_dialog"),
    )
}

@Composable
private fun ContactPersonEditorDialog(dialog: PartyDetailDialog.ContactPersonEditor, onEvent: (PartyDetailEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(PartyDetailEvent.DismissDialog) },
        title = { Text(if (dialog.contactPersonId == null) "Add contact person" else "Edit contact person") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = { onEvent(PartyDetailEvent.ContactFieldChanged(name = it)) },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth().testTag("party_detail_contact_name"),
                )
                OutlinedTextField(
                    value = dialog.designation,
                    onValueChange = { onEvent(PartyDetailEvent.ContactFieldChanged(designation = it)) },
                    label = { Text("Designation") },
                    modifier = Modifier.fillMaxWidth().testTag("party_detail_contact_designation"),
                )
                OutlinedTextField(
                    value = dialog.mobile,
                    onValueChange = { onEvent(PartyDetailEvent.ContactFieldChanged(mobile = it)) },
                    label = { Text("Mobile") },
                    modifier = Modifier.fillMaxWidth().testTag("party_detail_contact_mobile"),
                )
                OutlinedTextField(
                    value = dialog.email,
                    onValueChange = { onEvent(PartyDetailEvent.ContactFieldChanged(email = it)) },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth().testTag("party_detail_contact_email"),
                )
                Row {
                    Checkbox(
                        checked = dialog.isPrimary,
                        onCheckedChange = { onEvent(PartyDetailEvent.ContactFieldChanged(isPrimary = it)) },
                        modifier = Modifier
                            .testTag("party_detail_contact_primary")
                            .semantics { contentDescription = "Primary contact" },
                    )
                    Text("Primary contact")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(PartyDetailEvent.SaveContact) }, modifier = Modifier.testTag("party_detail_contact_save")) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(PartyDetailEvent.DismissDialog) }) { Text("Cancel") }
        },
        modifier = Modifier.testTag("party_detail_contact_dialog"),
    )
}

@Composable
private fun AddTagDialog(state: PartyDetailUiState, onEvent: (PartyDetailEvent) -> Unit) {
    var newTagName by remember { mutableStateOf("") }
    val assignedIds = state.tags.map { it.tagId }.toSet()
    AlertDialog(
        onDismissRequest = { onEvent(PartyDetailEvent.DismissDialog) },
        title = { Text("Add tag") },
        text = {
            Column {
                LazyColumn(modifier = Modifier.testTag("party_detail_tag_list")) {
                    items(state.allTags.filter { it.tagId !in assignedIds }, key = { it.tagId }) { tag ->
                        TextButton(
                            onClick = { onEvent(PartyDetailEvent.AssignExistingTag(tag.tagId)) },
                            modifier = Modifier.testTag("party_detail_pick_tag_${tag.tagId}"),
                        ) { Text(tag.path) }
                    }
                }
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("Or create a new tag") },
                    modifier = Modifier.fillMaxWidth().testTag("party_detail_new_tag_name"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(PartyDetailEvent.CreateAndAssignTag(newTagName)) },
                modifier = Modifier.testTag("party_detail_new_tag_create"),
            ) { Text("Create & add") }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(PartyDetailEvent.DismissDialog) }) { Text("Cancel") }
        },
        modifier = Modifier.testTag("party_detail_add_tag_dialog"),
    )
}

@Composable
private fun NoteEditorDialog(dialog: PartyDetailDialog.NoteEditor, onEvent: (PartyDetailEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(PartyDetailEvent.DismissDialog) },
        title = { Text(if (dialog.noteId == null) "Add note" else "Edit note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dialog.body,
                    onValueChange = { onEvent(PartyDetailEvent.NoteBodyChanged(it)) },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth().testTag("party_detail_note_body"),
                )

                Text("Type", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.testTag("party_detail_note_type_list")) {
                    items(NoteType.entries, key = { it.name }) { type ->
                        val selected = type == dialog.type
                        TextButton(
                            onClick = { onEvent(PartyDetailEvent.NoteTypeChanged(type)) },
                            modifier = Modifier.testTag("party_detail_note_type_${type.name}"),
                        ) { Text(if (selected) "✓ ${type.toUiLabel()}" else type.toUiLabel()) }
                    }
                }

                if (dialog.type.showsDueDate()) {
                    OutlinedTextField(
                        value = dialog.dueAtText,
                        onValueChange = { onEvent(PartyDetailEvent.NoteDueAtChanged(it)) },
                        label = { Text("Due date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth().testTag("party_detail_note_due_at"),
                    )
                }

                if (dialog.issueOptions.isNotEmpty()) {
                    Text("Part of an issue (optional)", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(modifier = Modifier.testTag("party_detail_note_issue_list")) {
                        items(dialog.issueOptions, key = { it.issueId }) { issue ->
                            val selected = issue.issueId == dialog.selectedIssueId
                            TextButton(
                                onClick = {
                                    onEvent(PartyDetailEvent.NoteIssueSelected(if (selected) null else issue.issueId))
                                },
                                modifier = Modifier.testTag("party_detail_note_issue_${issue.issueId}"),
                            ) { Text(if (selected) "✓ ${issue.title}" else issue.title) }
                        }
                    }
                }
                OutlinedTextField(
                    value = dialog.newIssueTitle,
                    onValueChange = { onEvent(PartyDetailEvent.NoteNewIssueTitleChanged(it)) },
                    label = { Text("Or start a new issue (optional)") },
                    modifier = Modifier.fillMaxWidth().testTag("party_detail_note_new_issue"),
                )

                // Voucher-linking is only offered when creating a note — editNote does not carry
                // linkedVoucherId (unchanged pre-1.2-A behavior), so showing this picker in edit
                // mode would be a false affordance: a selection here would be silently discarded.
                if (dialog.noteId == null) {
                    if (dialog.isLoadingVouchers) {
                        Box(modifier = Modifier.fillMaxWidth()) { CircularProgressIndicator() }
                    } else if (dialog.voucherOptions.isNotEmpty()) {
                        Text("Link a voucher (optional)", style = MaterialTheme.typography.labelMedium)
                        LazyColumn(modifier = Modifier.testTag("party_detail_note_voucher_list")) {
                            items(dialog.voucherOptions, key = { it.voucherId }) { option ->
                                val selected = option.voucherId == dialog.selectedVoucherId
                                TextButton(
                                    onClick = {
                                        onEvent(PartyDetailEvent.NoteVoucherSelected(if (selected) null else option.voucherId))
                                    },
                                    modifier = Modifier.testTag("party_detail_note_voucher_${option.voucherId}"),
                                ) { Text(if (selected) "✓ ${option.label}" else option.label) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(PartyDetailEvent.SaveNote) }, modifier = Modifier.testTag("party_detail_note_save")) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(PartyDetailEvent.DismissDialog) }) { Text("Cancel") }
        },
        modifier = Modifier.testTag("party_detail_note_editor_dialog"),
    )
}
