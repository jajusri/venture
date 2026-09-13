package com.jajusri.venture.feature.dincharya.presentation

import com.jajusri.venture.feature.dincharya.domain.model.FollowUpUrgency
import com.jajusri.venture.feature.masterdata.presentation.MasterDataUiError

data class DincharyaUiState(
    val companyId: String? = null,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: MasterDataUiError? = null,
    val followUps: List<FollowUpUi> = emptyList(),
    val followUpsMoreCount: Int = 0,
    val pendingConfirmations: List<PendingConfirmationUi> = emptyList(),
    val pendingConfirmationsMoreCount: Int = 0,
    val pendingContactCompletions: List<PendingContactCompletionUi> = emptyList(),
    val pendingContactCompletionsMoreCount: Int = 0,
) {
    val isBusy: Boolean get() = isInitialLoading || isRefreshing
    val hasAnyContent: Boolean
        get() = followUps.isNotEmpty() || pendingConfirmations.isNotEmpty() || pendingContactCompletions.isNotEmpty()
}

/** [urgency] drives the plain-language reason label a user sees ("Follow-up overdue" / "Follow-up
 * due today" / "Follow-up upcoming") — resolved to a string resource in Compose, never a raw
 * [com.jajusri.venture.feature.party.domain.model.NoteType] or internal enum name exposed directly.
 * [dueAt] stays a raw epoch-millis value; formatting to a display date happens in Compose at render
 * time, matching this codebase's existing convention (e.g. `PartyDetailScreen.kt`'s
 * `TIMELINE_DATE_FORMATTER`), not baked into UI state. */
data class FollowUpUi(
    val noteId: String,
    val partyId: String,
    val partyDisplayName: String,
    val body: String,
    val dueAt: Long,
    val urgency: FollowUpUrgency,
)

data class PendingConfirmationUi(
    val partyId: String,
    val partyDisplayName: String,
    val fieldsLabel: String,
)

data class PendingContactCompletionUi(
    val partyId: String,
    val partyDisplayName: String,
)

sealed interface DincharyaEvent {
    data object Load : DincharyaEvent
    data object Refresh : DincharyaEvent
    data object Retry : DincharyaEvent
    data class ItemTapped(val partyId: String) : DincharyaEvent
}

sealed interface DincharyaEffect {
    data class OpenPartyDetail(val partyId: String) : DincharyaEffect
}
