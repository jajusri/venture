package com.jajusri.venture.feature.party.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PartyFieldProvenanceDao {
    @Query(
        "SELECT * FROM party_field_provenance WHERE companyId = :companyId AND partyId = :partyId AND fieldName = :fieldName",
    )
    suspend fun findField(companyId: String, partyId: String, fieldName: String): PartyFieldProvenanceEntity?

    @Query("SELECT * FROM party_field_provenance WHERE companyId = :companyId AND partyId = :partyId")
    suspend fun findAllForParty(companyId: String, partyId: String): List<PartyFieldProvenanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PartyFieldProvenanceEntity)

    /**
     * MVP-1.2-D Dincharya Type B (Pending Tally Confirmation) — the company-wide analog of
     * [findAllForParty]. Reads the existing `state = 'exported'` provenance rows directly (MVP-1.1-D),
     * never a new Tally state machine. Grouped **per Party** (`GROUP BY companyId, partyId`), not
     * one row per pending field — a Party with several fields still awaiting re-sync surfaces as one
     * Dincharya item, not several near-duplicate-looking rows for the same Party (avoids the
     * "duplicate Dincharya items" risk named in the readiness review). `GROUP_CONCAT(fieldName, ',')`
     * mirrors the already-established `fieldNamesCsv` convention on `party_export_events` (split and
     * mapped through [com.jajusri.venture.feature.party.domain.model.TallyExportFieldMapping.labelFor]
     * for display — never a raw field name shown to the user). `earliestAt` prefers
     * `lastExportedAt` (falls back to `updatedAt` for the rare case it's unset) so the ordering
     * reflects how long a field has genuinely been waiting, not merely when the row last changed.
     * Clears the moment every field for a Party leaves `exported` (confirmed or conflicted) — a pure
     * query-level filter, never a manually curated list. `companyId` is bound directly in SQL.
     */
    @Query(
        """
        SELECT companyId, partyId, GROUP_CONCAT(fieldName, ',') AS fieldNamesCsv,
               MIN(COALESCE(lastExportedAt, updatedAt)) AS earliestAt
        FROM party_field_provenance
        WHERE companyId = :companyId AND state = 'exported'
        GROUP BY companyId, partyId
        ORDER BY earliestAt ASC, partyId ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun pagePendingConfirmationForCompany(companyId: String, limit: Int, offset: Int): List<PendingConfirmationRow>

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT partyId FROM party_field_provenance
            WHERE companyId = :companyId AND state = 'exported'
            GROUP BY partyId
        )
        """,
    )
    suspend fun countPendingConfirmationForCompany(companyId: String): Int
}

data class PendingConfirmationRow(
    val companyId: String,
    val partyId: String,
    val fieldNamesCsv: String,
    val earliestAt: Long,
)
