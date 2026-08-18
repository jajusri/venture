package com.budcom.android.feature.party.domain.model

/**
 * MVP-1.2-B Relationship Timeline (architecture §10, locked by PDL-014 as the unified historical
 * presentation for a Party). One chronological feed merging every locally-available, truthfully-
 * sourced relationship event for a Party — never a fabricated entry. [NoteEvent] and [ExportEvent]
 * are this milestone's two sources (notes + the existing Tally-export audit trail).
 */
sealed interface TimelineEntry {
    val timestamp: Long

    data class NoteEvent(val note: PartyNote) : TimelineEntry {
        override val timestamp: Long get() = note.createdAt
    }

    data class ExportEvent(val event: PartyExportEvent) : TimelineEntry {
        override val timestamp: Long get() = event.createdAt
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
