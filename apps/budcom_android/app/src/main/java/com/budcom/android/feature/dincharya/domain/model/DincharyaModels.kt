package com.budcom.android.feature.dincharya.domain.model

import com.budcom.android.feature.party.domain.model.NoteType

/**
 * MVP-1.2-D Dincharya — a deterministic, action-focused operational worklist (architecture §10/§11,
 * locked by PDL-018). Exactly three item types, each with a deterministic source, ordering, and
 * disappearance condition (PDL-018) — never a generative/AI-ranked feed. Every item always carries
 * enough identity ([partyId]/[partyDisplayName]) to deep-link straight to that Party's existing
 * Detail screen (`Routes.partyDetail`) — Dincharya never opens a second, parallel detail view.
 */
sealed interface DincharyaItem {
    val partyId: String
    val partyDisplayName: String

    /**
     * Type A — a `commitment`/`follow_up` [com.budcom.android.feature.party.domain.model.PartyNote]
     * with a due date that is not yet completed (architecture §9.1, MVP-1.2-A columns). Never
     * auto-expires (PDL-018) — remains active until completed or rescheduled by the user on Party
     * Detail; this item type only ever reflects that same real note state, never a separate copy.
     */
    data class FollowUp(
        val noteId: String,
        override val partyId: String,
        override val partyDisplayName: String,
        val noteType: NoteType,
        val body: String,
        val dueAt: Long,
        val urgency: FollowUpUrgency,
    ) : DincharyaItem

    /**
     * Type B — a Party with at least one Tally-compatible field currently
     * [com.budcom.android.feature.party.domain.model.FieldProvenanceState.Exported] (MVP-1.1-D),
     * i.e. genuinely awaiting a live Tally re-sync to confirm or conflict. Reads the existing
     * provenance state directly — never a new Tally state machine, never a new Connector call
     * (architecture §13). Disappears the moment every field for that Party clears out of `exported`
     * (confirmed or conflicted), never merely because the user opened this screen.
     */
    data class PendingTallyConfirmation(
        override val partyId: String,
        override val partyDisplayName: String,
        val pendingFieldLabels: List<String>,
        val earliestExportedAt: Long,
    ) : DincharyaItem

    /**
     * Type C — a non-Prospect Party with neither a valid phone nor a valid email (PDL-018's locked
     * contact-completeness rule: contact-complete = valid phone OR valid email). "Valid phone"
     * reuses [com.budcom.android.core.util.PhoneNumberNormalizer] exactly (the same
     * `primaryPhoneNormalized` column every other Party surface already relies on) — never a second
     * phone-validation implementation. Clears the instant either field becomes valid.
     */
    data class PendingContactCompletion(
        override val partyId: String,
        override val partyDisplayName: String,
    ) : DincharyaItem
}

/** Presentation-neutral urgency classification for a [DincharyaItem.FollowUp], derived purely from
 * comparing [DincharyaItem.FollowUp.dueAt] to the current date — never an invented "priority score"
 * (PDL-018). Ordering itself is `dueAt ASC` at the query layer, which already yields
 * Overdue-then-DueToday-then-Upcoming for free since overdue timestamps sort before today's, which
 * sort before future ones; this enum only drives the plain-language reason label. */
enum class FollowUpUrgency {
    Overdue,
    DueToday,
    Upcoming,
}

/**
 * One bounded, capped-count group (architecture §10 — "capped count with clear 'N more'
 * disclosure", never an infinite scroll). [items] is already limited to the requested cap;
 * [totalItems] is the true, uncapped count so the UI can show how many more exist.
 */
data class DincharyaGroup<T : DincharyaItem>(
    val items: List<T>,
    val totalItems: Int,
) {
    val moreCount: Int get() = (totalItems - items.size).coerceAtLeast(0)
}

/** The full Dincharya read for one company — one bounded group per deterministic item type,
 * never one undifferentiated mixed list (architecture §10). */
data class DincharyaSnapshot(
    val followUps: DincharyaGroup<DincharyaItem.FollowUp>,
    val pendingConfirmations: DincharyaGroup<DincharyaItem.PendingTallyConfirmation>,
    val pendingContactCompletions: DincharyaGroup<DincharyaItem.PendingContactCompletion>,
) {
    val isEmpty: Boolean
        get() = followUps.items.isEmpty() && pendingConfirmations.items.isEmpty() && pendingContactCompletions.items.isEmpty()
}
