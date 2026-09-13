package com.jajusri.venture.feature.party.domain.model

/**
 * MVP-1.2-B/C Relationship Timeline (architecture §10, locked by PDL-014 as the unified historical
 * presentation for a Party). One chronological feed merging every locally-available, truthfully-
 * sourced relationship event for a Party — never a fabricated entry. [NoteEvent]/[ExportEvent]
 * (1.2-B) are notes and the existing Tally-export audit trail; [IssueOpenedEvent]/
 * [IssueResolvedEvent] (1.2-C) are derived live from [PartyIssue]'s own real timestamps — never a
 * separately-stored, independently-writable record, so the Timeline can never drift from what the
 * Issues section itself shows for the same issue (architecture §16 "Issue/Timeline Consistency").
 */
sealed interface TimelineEntry {
    val timestamp: Long

    data class NoteEvent(val note: PartyNote) : TimelineEntry {
        override val timestamp: Long get() = note.createdAt
    }

    data class ExportEvent(val event: PartyExportEvent) : TimelineEntry {
        override val timestamp: Long get() = event.createdAt
    }

    data class IssueOpenedEvent(val issueId: String, val title: String, val openedAt: Long) : TimelineEntry {
        override val timestamp: Long get() = openedAt
    }

    /** Present only while the issue is *currently* resolved — reopening clears [PartyIssue.resolvedAt],
     * so a past resolution is not retained as a permanent history entry. No separate append-only
     * issue-event log exists; building one is out of this milestone's scope (accepted limitation,
     * architecture §9.4's "no generic activity type plugin system" discipline extended here). Never
     * fabricated: this reads the issue's own real [PartyIssue.resolvedAt], live. */
    data class IssueResolvedEvent(val issueId: String, val title: String, val resolvedAt: Long) : TimelineEntry {
        override val timestamp: Long get() = resolvedAt
    }
}

data class TimelineEntryPage(
    val items: List<TimelineEntry>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
) {
    val canLoadMore: Boolean get() = page * pageSize < totalItems
}

/** Per-issue note-count/last-activity rollup (MVP-1.2-C) — computed from real [PartyNote] rows
 * linked via [PartyNote.issueId], never estimated. */
data class IssueActivitySummary(
    val noteCount: Int,
    val latestNoteAt: Long,
)
