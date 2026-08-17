package com.budcom.android.feature.masterdata.ledger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface LedgerDao {
    @Query("SELECT COUNT(*) FROM cached_ledgers WHERE companyId = :companyId")
    suspend fun countForCompany(companyId: String): Int

    @Query("SELECT * FROM cached_ledgers WHERE companyId = :companyId AND id = :ledgerId")
    suspend fun findById(companyId: String, ledgerId: String): LedgerEntity?

    /** Every cached ledger for a company, unpaged — used only by local-only reconciliation
     * (see [com.budcom.android.feature.masterdata.ledger.domain.port.LedgerSnapshotPort]), never
     * by the paged/searched Ledger Browser UI path. */
    @Query("SELECT * FROM cached_ledgers WHERE companyId = :companyId")
    suspend fun getAllForCompany(companyId: String): List<LedgerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LedgerEntity>)

    @Query("DELETE FROM cached_ledgers WHERE companyId = :companyId")
    suspend fun deleteForCompany(companyId: String)

    @Transaction
    suspend fun replaceAllForCompany(companyId: String, entities: List<LedgerEntity>) {
        deleteForCompany(companyId)
        if (entities.isNotEmpty()) {
            upsertAll(entities)
        }
    }

    @Query(
        """
        SELECT * FROM cached_ledgers
        WHERE companyId = :companyId
          AND (
            :query IS NULL
            OR name LIKE '%' || :query || '%' COLLATE NOCASE
            OR IFNULL(alias, '') LIKE '%' || :query || '%' COLLATE NOCASE
            OR IFNULL(parentGroup, '') LIKE '%' || :query || '%' COLLATE NOCASE
          )
        ORDER BY
          CASE WHEN :sortBy = 'parentGroup' AND :ascending = 1 THEN parentGroup END COLLATE NOCASE ASC,
          CASE WHEN :sortBy = 'parentGroup' AND :ascending = 0 THEN parentGroup END COLLATE NOCASE DESC,
          CASE WHEN :sortBy = 'syncedAt' AND :ascending = 1 THEN syncedAt END ASC,
          CASE WHEN :sortBy = 'syncedAt' AND :ascending = 0 THEN syncedAt END DESC,
          CASE WHEN :sortBy = 'closingBalance' AND :ascending = 1 THEN closingAmount END ASC,
          CASE WHEN :sortBy = 'closingBalance' AND :ascending = 0 THEN closingAmount END DESC,
          CASE WHEN :ascending = 1 THEN name END COLLATE NOCASE ASC,
          CASE WHEN :ascending = 0 THEN name END COLLATE NOCASE DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun queryPage(
        companyId: String,
        query: String?,
        sortBy: String,
        ascending: Int,
        limit: Int,
        offset: Int,
    ): List<LedgerEntity>

    @Query(
        """
        SELECT COUNT(*) FROM cached_ledgers
        WHERE companyId = :companyId
          AND (
            :query IS NULL
            OR name LIKE '%' || :query || '%' COLLATE NOCASE
            OR IFNULL(alias, '') LIKE '%' || :query || '%' COLLATE NOCASE
            OR IFNULL(parentGroup, '') LIKE '%' || :query || '%' COLLATE NOCASE
          )
        """,
    )
    suspend fun countMatching(companyId: String, query: String?): Int
}
