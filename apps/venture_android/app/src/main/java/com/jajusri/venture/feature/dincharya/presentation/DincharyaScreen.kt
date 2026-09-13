package com.jajusri.venture.feature.dincharya.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jajusri.venture.R
import com.jajusri.venture.feature.dincharya.domain.model.FollowUpUrgency
import com.jajusri.venture.feature.masterdata.presentation.MasterDataErrorBlock
import com.jajusri.venture.feature.masterdata.presentation.MasterDataLoadingIndicator
import com.jajusri.venture.feature.masterdata.presentation.displayMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DincharyaRoute(
    onOpenPartyDetail: (String) -> Unit,
    viewModel: DincharyaViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DincharyaEffect.OpenPartyDetail -> onOpenPartyDetail(effect.partyId)
            }
        }
    }

    DincharyaScreen(state = state, onEvent = viewModel::onEvent)
}

/**
 * Dincharya (MVP-1.2-D) — grouped by the three deterministic item types (architecture §10), each
 * group capped with an explicit "N more" disclosure rather than an infinite scroll. Reuses the same
 * shared loading/error components and honest-empty-state discipline already established by
 * [com.jajusri.venture.feature.connect.presentation.ConnectScreen] — no new visual system introduced.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun DincharyaScreen(
    state: DincharyaUiState,
    onEvent: (DincharyaEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { onEvent(DincharyaEvent.Refresh) },
    )

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("dincharya_screen"),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.dincharya_title)) })
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pullRefresh(pullRefreshState),
        ) {
            when {
                state.isInitialLoading && !state.hasAnyContent -> {
                    MasterDataLoadingIndicator(testTag = "dincharya_loading")
                }
                state.error != null && !state.hasAnyContent -> {
                    MasterDataErrorBlock(
                        error = state.error,
                        onRetry = { onEvent(DincharyaEvent.Retry) },
                        errorTestTag = "dincharya_error",
                        retryTestTag = "dincharya_retry",
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("dincharya_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Text(
                                text = stringResource(R.string.dincharya_oi_framing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("dincharya_oi_framing"),
                            )
                        }

                        if (!state.hasAnyContent) {
                            item {
                                Text(
                                    text = stringResource(R.string.dincharya_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.fillMaxWidth().testTag("dincharya_empty"),
                                )
                            }
                        } else {
                            // A refresh failure while stale content remains on screen must still be
                            // stated honestly, never silently swallowed (mirrors ConnectScreen's own
                            // "connect_inline_error" discipline).
                            if (state.error != null) {
                                item {
                                    Text(
                                        text = state.error.displayMessage(),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.testTag("dincharya_inline_error"),
                                    )
                                }
                            }
                            if (state.followUps.isNotEmpty()) {
                                item {
                                    DincharyaSectionHeader(
                                        title = stringResource(R.string.dincharya_section_followups),
                                        testTag = "dincharya_section_followups",
                                    )
                                }
                                items(state.followUps, key = { "followup_${it.noteId}" }) { row ->
                                    FollowUpCard(row = row, onEvent = onEvent)
                                }
                                if (state.followUpsMoreCount > 0) {
                                    item { MoreCountText(count = state.followUpsMoreCount, testTag = "dincharya_followups_more") }
                                }
                            }

                            if (state.pendingConfirmations.isNotEmpty()) {
                                item {
                                    DincharyaSectionHeader(
                                        title = stringResource(R.string.dincharya_section_confirmations),
                                        testTag = "dincharya_section_confirmations",
                                    )
                                }
                                items(state.pendingConfirmations, key = { "confirmation_${it.partyId}" }) { row ->
                                    PendingConfirmationCard(row = row, onEvent = onEvent)
                                }
                                if (state.pendingConfirmationsMoreCount > 0) {
                                    item { MoreCountText(count = state.pendingConfirmationsMoreCount, testTag = "dincharya_confirmations_more") }
                                }
                            }

                            if (state.pendingContactCompletions.isNotEmpty()) {
                                item {
                                    DincharyaSectionHeader(
                                        title = stringResource(R.string.dincharya_section_contact),
                                        testTag = "dincharya_section_contact",
                                    )
                                }
                                items(state.pendingContactCompletions, key = { "contact_${it.partyId}" }) { row ->
                                    PendingContactCard(row = row, onEvent = onEvent)
                                }
                                if (state.pendingContactCompletionsMoreCount > 0) {
                                    item { MoreCountText(count = state.pendingContactCompletionsMoreCount, testTag = "dincharya_contact_more") }
                                }
                            }
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = state.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter).testTag("dincharya_refresh_indicator"),
            )
        }
    }
}

@Composable
private fun DincharyaSectionHeader(title: String, testTag: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
}

@Composable
private fun MoreCountText(count: Int, testTag: String) {
    Text(
        text = stringResource(R.string.dincharya_more_items, count),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
}

@Composable
private fun FollowUpCard(row: FollowUpUi, onEvent: (DincharyaEvent) -> Unit) {
    val reasonText = when (row.urgency) {
        FollowUpUrgency.Overdue -> stringResource(R.string.dincharya_reason_followup_overdue)
        FollowUpUrgency.DueToday -> stringResource(R.string.dincharya_reason_followup_due_today)
        FollowUpUrgency.Upcoming -> stringResource(R.string.dincharya_reason_followup_upcoming)
    }
    // Color is a secondary signal only — the reason is always stated in text first, never by
    // color alone (locked visual principle: color communicates state, never decoration).
    val reasonColor = if (row.urgency == FollowUpUrgency.Overdue) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dateLabel = formatDincharyaDate(row.dueAt)
    Card(
        onClick = { onEvent(DincharyaEvent.ItemTapped(row.partyId)) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dincharya_followup_${row.noteId}")
            .semantics {
                contentDescription = "${row.partyDisplayName}, $reasonText, due $dateLabel. ${row.body}"
            },
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = row.partyDisplayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = reasonText, style = MaterialTheme.typography.bodySmall, color = reasonColor)
            Text(text = dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = row.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PendingConfirmationCard(row: PendingConfirmationUi, onEvent: (DincharyaEvent) -> Unit) {
    val reasonText = stringResource(R.string.dincharya_reason_confirmation)
    val fieldsText = stringResource(R.string.dincharya_confirmation_fields_prefix, row.fieldsLabel)
    Card(
        onClick = { onEvent(DincharyaEvent.ItemTapped(row.partyId)) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dincharya_confirmation_${row.partyId}")
            .semantics { contentDescription = "${row.partyDisplayName}, $reasonText. $fieldsText" },
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = row.partyDisplayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = reasonText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = fieldsText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PendingContactCard(row: PendingContactCompletionUi, onEvent: (DincharyaEvent) -> Unit) {
    val reasonText = stringResource(R.string.dincharya_reason_contact)
    Card(
        onClick = { onEvent(DincharyaEvent.ItemTapped(row.partyId)) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dincharya_contact_${row.partyId}")
            .semantics { contentDescription = "${row.partyDisplayName}, $reasonText" },
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = row.partyDisplayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = reasonText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Matches this codebase's existing display-date convention exactly (e.g. `PartyDetailScreen.kt`'s
 * `TIMELINE_DATE_FORMATTER`) — `dd MMM yyyy` in the device's local zone. */
private val DINCHARYA_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

private fun formatDincharyaDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(DINCHARYA_DATE_FORMATTER)
