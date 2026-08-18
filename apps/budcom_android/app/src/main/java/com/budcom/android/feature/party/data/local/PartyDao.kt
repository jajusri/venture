package com.budcom.android.feature.party.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PartyDao {
    @Query("SELECT COUNT(*) FROM cached_parties WHERE companyId = :companyId")
    suspend fun countForCompany(companyId: String): Int

    @Query("SELECT * FROM cached_parties WHERE companyId = :companyId AND partyId = :partyId")
    suspend fun findById(companyId: String, partyId: String): PartyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PartyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PartyEntity>)

    @Query(
        """
        SELECT * FROM cached_parties
        WHERE companyId = :companyId AND classification = :classification
        ORDER BY displayName COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun pageByClassification(
        companyId: String,
        classification: String,
        limit: Int,
        offset: Int,
    ): List<PartyEntity>

    @Query("SELECT COUNT(*) FROM cached_parties WHERE companyId = :companyId AND classification = :classification")
    suspend fun countByClassification(companyId: String, classification: String): Int

    @Query(
        """
        SELECT * FROM cached_parties
        WHERE companyId = :companyId
          AND (:classification IS NULL OR classification = :classification)
          AND (
            displayName LIKE '%' || :query || '%' COLLATE NOCASE
            OR IFNULL(primaryPhoneNormalized, '') LIKE '%' || :query || '%'
          )
        ORDER BY displayName COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun search(companyId: String, query: String, classification: String?, limit: Int, offset: Int): List<PartyEntity>

    @Query(
        """
        SELECT COUNT(*) FROM cached_parties
        WHERE companyId = :companyId
          AND (:classification IS NULL OR classification = :classification)
          AND (
            displayName LIKE '%' || :query || '%' COLLATE NOCASE
            OR IFNULL(primaryPhoneNormalized, '') LIKE '%' || :query || '%'
          )
        """,
    )
    suspend fun countSearch(companyId: String, query: String, classification: String?): Int

    /**
     * MVP-1.2-D Dincharya Type C (Pending Contact Completion) — the first genuinely company-wide,
     * cross-party query on this DAO (architecture §13/§20 Risk #1). Locked rule (PDL-018): a Party
     * is contact-complete when it has a valid phone OR a valid email; this returns only Parties
     * missing *both*. "Valid phone" reuses [PartyEntity.primaryPhoneNormalized] exactly as already
     * computed by [com.budcom.android.core.util.PhoneNumberNormalizer.normalizeForSearch] — never a
     * second phone-validation rule. "Valid email" mirrors this codebase's existing convention of not
     * imposing any email-format check anywhere: non-blank is the only bar applied. Prospects are
     * explicitly excluded (PDL-018) — contact-person completeness is out of Dincharya v1 scope
     * entirely, untouched here. `companyId` is bound directly in SQL, the only isolation boundary
     * for a query with no secondary `partyId` narrowing to lean on.
     */
    @Query(
        """
        SELECT * FROM cached_parties
        WHERE companyId = :companyId
          AND classification != 'prospect'
          AND primaryPhoneNormalized IS NULL
          AND (primaryEmail IS NULL OR TRIM(primaryEmail) = '')
        ORDER BY displayName COLLATE NOCASE ASC, partyId ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun pageMissingContactInfo(companyId: String, limit: Int, offset: Int): List<PartyEntity>

    @Query(
        """
        SELECT COUNT(*) FROM cached_parties
        WHERE companyId = :companyId
          AND classification != 'prospect'
          AND primaryPhoneNormalized IS NULL
          AND (primaryEmail IS NULL OR TRIM(primaryEmail) = '')
        """,
    )
    suspend fun countMissingContactInfo(companyId: String): Int

    /** Bounded bulk-by-id lookup (never one query per row) — used by Dincharya's follow-up and
     * pending-confirmation groups to resolve display names for the current page's Parties in one
     * round-trip, mirroring Connect's own bulk-enrichment-read precedent
     * ([com.budcom.android.feature.party.data.repository.PartyRepositoryImpl]'s
     * `getSourceLinksForCompany`/`getTagsForCompany` pattern) rather than a cross-table SQL JOIN. */
    @Query("SELECT * FROM cached_parties WHERE companyId = :companyId AND partyId IN (:partyIds)")
    suspend fun findByIds(companyId: String, partyIds: List<String>): List<PartyEntity>
}
