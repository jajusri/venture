package com.jajusri.venture.feature.party.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PartyNoteDao {
    @Query(
        "SELECT * FROM party_notes WHERE companyId = :companyId AND partyId = :partyId " +
            "ORDER BY createdAt DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun pageForParty(companyId: String, partyId: String, limit: Int, offset: Int): List<PartyNoteEntity>

    @Query("SELECT COUNT(*) FROM party_notes WHERE companyId = :companyId AND partyId = :partyId")
    suspend fun countForParty(companyId: String, partyId: String): Int

    @Query("SELECT * FROM party_notes WHERE companyId = :companyId AND noteId = :noteId")
    suspend fun findById(companyId: String, noteId: String): PartyNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PartyNoteEntity)

    @Query("DELETE FROM party_notes WHERE companyId = :companyId AND noteId = :noteId")
    suspend fun delete(companyId: String, noteId: String)

    /** One row per issue this party has at least one note under — note count + latest note
     * timestamp, for the Issues section's card summary (MVP-1.2-C). A single bounded, indexed
     * (`companyId`, `partyId` prefix of the existing index) aggregate query — never N+1. */
    @Query(
        "SELECT issueId, COUNT(*) AS noteCount, MAX(createdAt) AS latestNoteAt FROM party_notes " +
            "WHERE companyId = :companyId AND partyId = :partyId AND issueId IS NOT NULL GROUP BY issueId",
    )
    suspend fun issueActivitySummary(companyId: String, partyId: String): List<IssueActivityRow>

    /**
     * MVP-1.2-D Dincharya Type A (Follow-ups/Callbacks) — the company-wide analog of
     * [pageForParty]. Locked eligibility (PDL-018): `type IN ('commitment','follow_up')`,
     * `dueAt IS NOT NULL`, `completedAt IS NULL` — reuses 1.2-A's exact due-date/completion
     * semantics unchanged, no new date logic. **No automatic expiry**: an overdue note stays
     * eligible indefinitely until completed or rescheduled (PDL-018) — there is deliberately no
     * `dueAt` lower bound in this WHERE clause. `ORDER BY dueAt ASC, noteId ASC` yields the locked
     * ordering (overdue-first, then due-today, then upcoming, earliest-due-date-first within each
     * group) for free, since overdue timestamps are chronologically earliest, plus a stable
     * secondary tie-break so paging never duplicates/drops a row on a shared `dueAt`. `companyId`
     * is bound directly in SQL — the sole isolation boundary for this cross-party query.
     */
    @Query(
        """
        SELECT * FROM party_notes
        WHERE companyId = :companyId
          AND type IN ('commitment', 'follow_up')
          AND dueAt IS NOT NULL
          AND completedAt IS NULL
        ORDER BY dueAt ASC, noteId ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun pageFollowUpsForCompany(companyId: String, limit: Int, offset: Int): List<PartyNoteEntity>

    @Query(
        """
        SELECT COUNT(*) FROM party_notes
        WHERE companyId = :companyId
          AND type IN ('commitment', 'follow_up')
          AND dueAt IS NOT NULL
          AND completedAt IS NULL
        """,
    )
    suspend fun countFollowUpsForCompany(companyId: String): Int
}

data class IssueActivityRow(val issueId: String, val noteCount: Int, val latestNoteAt: Long)
