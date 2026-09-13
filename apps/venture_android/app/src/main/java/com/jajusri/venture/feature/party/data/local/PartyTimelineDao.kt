package com.jajusri.venture.feature.party.data.local

import androidx.room.Dao
import androidx.room.Query

/**
 * One row of the merged Relationship Timeline projection (MVP-1.2-B/C) — never a stored table, no
 * migration required. `kind` discriminates `party_notes` rows (`"note"`), `party_export_events`
 * rows (`"export"`), and `party_issues`-derived rows (`"issue_opened"`/`"issue_resolved"`, 1.2-C);
 * the unused columns for each source are `NULL` so every arm of the `UNION ALL` shares one column
 * shape. `body` doubles as the issue title for issue-kind rows.
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
 * Read-only merge of [PartyNoteDao]'s `party_notes`, [PartyExportEventDao]'s `party_export_events`,
 * and (MVP-1.2-C) [PartyIssueDao]'s `party_issues` lifecycle timestamps into one chronological,
 * bounded, company/party-scoped feed — the Relationship Timeline (architecture §10/§11/§16, locked
 * by PDL-014). Deliberately its own `@Dao` rather than living on any one source DAO, since the
 * merge belongs to none of them alone. Requires no new table/migration: every source table already
 * exists (schema unchanged since `MIGRATION_8_9`).
 *
 * The issue arms are read live from `party_issues`' own `createdAt`/`resolvedAt` — never a
 * separately-stored, independently-writable record — so creating/resolving/reopening an issue is
 * reflected here automatically, with no risk of the Timeline and the Issues section ever
 * disagreeing about the same issue (architecture §16 "Issue/Timeline Consistency"). Accepted
 * limitation: reopening an issue clears `resolvedAt`, so a *past* resolution does not remain as a
 * permanent history entry — no append-only issue-event log exists, and adding one is out of this
 * milestone's scope (documented, not silently accepted).
 *
 * `issueId` narrows to one issue's notes only (export events and issue-lifecycle rows are excluded
 * entirely when set, since "tap an issue to see its notes," architecture §10, means notes only —
 * not a recursive re-showing of the very issue card the user is already looking at).
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

            UNION ALL

            SELECT 'issue_opened' AS kind, issueId AS id, companyId, partyId, createdAt AS timestamp, updatedAt,
                   title AS body, NULL AS linkedVoucherId, NULL AS type, NULL AS dueAt, NULL AS completedAt, NULL AS issueId,
                   NULL AS outputFileName, NULL AS fieldNamesCsv
            FROM party_issues
            WHERE companyId = :companyId AND partyId = :partyId AND :issueId IS NULL

            UNION ALL

            SELECT 'issue_resolved' AS kind, issueId AS id, companyId, partyId, resolvedAt AS timestamp, updatedAt,
                   title AS body, NULL AS linkedVoucherId, NULL AS type, NULL AS dueAt, NULL AS completedAt, NULL AS issueId,
                   NULL AS outputFileName, NULL AS fieldNamesCsv
            FROM party_issues
            WHERE companyId = :companyId AND partyId = :partyId AND resolvedAt IS NOT NULL AND :issueId IS NULL
        )
        ORDER BY timestamp DESC, id ASC, kind ASC
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

            UNION ALL

            SELECT issueId AS id FROM party_issues
            WHERE companyId = :companyId AND partyId = :partyId AND :issueId IS NULL

            UNION ALL

            SELECT issueId AS id FROM party_issues
            WHERE companyId = :companyId AND partyId = :partyId AND resolvedAt IS NOT NULL AND :issueId IS NULL
        )
        """,
    )
    suspend fun countTimelineForParty(companyId: String, partyId: String, issueId: String?): Int
}
