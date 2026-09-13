package com.jajusri.venture.feature.party.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PartyIssueDao {
    /** Open issues first, then resolved, newest first within each — 'open' sorts before
     * 'resolved' alphabetically, so a plain `status ASC` gives the desired ordering for free.
     * Unpaged: an individual Party's issue count is expected to stay small, exactly like this
     * feature's existing unpaged per-party tag/contact-person reads. */
    @Query(
        "SELECT * FROM party_issues WHERE companyId = :companyId AND partyId = :partyId " +
            "ORDER BY status ASC, createdAt DESC",
    )
    suspend fun findAllForParty(companyId: String, partyId: String): List<PartyIssueEntity>

    @Query("SELECT * FROM party_issues WHERE companyId = :companyId AND issueId = :issueId")
    suspend fun findById(companyId: String, issueId: String): PartyIssueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PartyIssueEntity)
}
