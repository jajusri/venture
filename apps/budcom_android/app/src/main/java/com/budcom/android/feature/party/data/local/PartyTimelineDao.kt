package com.budcom.android.feature.party.data.local

import androidx.room.Dao
import androidx.room.Query

/**
 * One row of the merged Relationship Timeline projection (MVP-1.2-B) — never a stored table, no
 * migration required. `kind` discriminates `party_notes` rows (`"note"`) from `party_export_events`
 * rows (`"export"`); the unused columns for each source are `NULL` so both arms of the `UNION ALL`
 * share one column shape.
 */
data class TimelineRowEntity(
    val kind: String,
    val id: String,
    val companyId: String,
    val partyId: String,
    val timestamp: Long,
    val updatedAt: Long,
    val body: String?,
    val linkedVoucherId: String?,
    val type: String?,
    val dueAt: Long?,
    val completedAt: Long?,
    val issueId: String?,
    val outputFileName: String?,
    val fieldNamesCsv: String?,
)

/**
 * Read-only merge of [PartyNoteDao]'s `party_notes` and [PartyExportEventDao]'s
 * `party_export_events` into one chronological, bounded, company/party-scoped feed — the
 * Relationship Timeline (architecture §10/§11, locked by PDL-014). Deliberately its own `@Dao`
 * rather than living on either source DAO, since it belongs to neither table alone. Requires no
 * new table/migration: both source tables already exist (schema unchanged since `MIGRATION_8_9`).
 *
 * `issueId` narrows to one issue's notes only (export events are excluded entirely when set, since
 * they have no issue association) — built now so MVP-1.2-C's "tap an issue to see its notes" can
 * reuse this exact query rather than a second, duplicate list implementation (architecture §10).
 */
@Dao
interface PartyTimelineDao {
    @Query(
        """
        SELECT * FROM (
            SELECT 'note' AS kind, noteId AS id, companyId, partyId, createdAt AS timestamp, updatedAt,
                   body, linkedVoucherId, type, dueAt, completedAt, issueId,
                   NULL AS outputFileName, NULL AS fieldNamesCsv
            FROM party_notes
            WHERE companyId = :companyId AND partyId = :partyId
              AND (:issueId IS NULL OR issueId = :issueId)

            UNION ALL

            SELECT 'export' AS kind, exportId AS id, companyId, partyId, createdAt AS timestamp, createdAt AS updatedAt,
                   NULL AS body, NULL AS linkedVoucherId, NULL AS type, NULL AS dueAt, NULL AS completedAt, NULL AS issueId,
                   outputFileName, fieldNamesCsv
            FROM party_export_events
            WHERE companyId = :companyId AND partyId = :partyId AND :issueId IS NULL
        )
        ORDER BY timestamp DESC, id ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun pageTimelineForParty(
        companyId: String,
        partyId: String,
        issueId: String?,
        limit: Int,
        offset: Int,
    ): List<TimelineRowEntity>

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT noteId AS id FROM party_notes
            WHERE companyId = :companyId AND partyId = :partyId
              AND (:issueId IS NULL OR issueId = :issueId)

            UNION ALL

            SELECT exportId AS id FROM party_export_events
            WHERE companyId = :companyId AND partyId = :partyId AND :issueId IS NULL
        )
        """,
    )
    suspend fun countTimelineForParty(companyId: String, partyId: String, issueId: String?): Int
}
