package com.jajusri.venture.feature.masterdata.stockitem.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface StockItemDao {
    @Query("SELECT COUNT(*) FROM cached_stock_items WHERE companyId = :companyId")
    suspend fun countForCompany(companyId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<StockItemEntity>)

    @Query("DELETE FROM cached_stock_items WHERE companyId = :companyId")
    suspend fun deleteForCompany(companyId: String)

    /** Single-row lookup by stable id -- used by Catalogue (MVP-1.4) to resolve a linked Stock
     * Item's Tally-authoritative display fields live at read time, never by mirroring a copy. */
    @Query("SELECT * FROM cached_stock_items WHERE companyId = :companyId AND id = :id")
    suspend fun findById(companyId: String, id: String): StockItemEntity?

    /** Unpaged, whole-company read -- used by Catalogue (MVP-1.4) for its own reconciliation sweep
     * (rename/disappearance/reappearance detection) and manual stock-item-to-link picker, never
     * for a user-facing paged list (that remains [queryPage]). */
    @Query("SELECT * FROM cached_stock_items WHERE companyId = :companyId")
    suspend fun findAllForCompany(companyId: String): List<StockItemEntity>

    /** Batched multi-id lookup -- lets a caller resolve N stock items in one query instead of N
     * single-row [findById] round trips (Catalogue perf package: was the reconciliation sweep's
     * own N+1). Room expands `:ids` into the right number of `?` placeholders for the `IN` clause. */
    @Query("SELECT * FROM cached_stock_items WHERE companyId = :companyId AND id IN (:ids)")
    suspend fun findByIds(companyId: String, ids: List<String>): List<StockItemEntity>

    /** Cheap freshness fingerprint for the whole company's cache -- a single aggregate query that
     * never fetches or deserializes row content, unlike [findAllForCompany]. Two equal fingerprints
     * mean the cache has not changed (no insert/delete/re-sync) since the first was taken; used by
     * Catalogue to skip a resume-triggered reconciliation sweep when nothing could have changed. */
    @Query("SELECT COUNT(*) || ':' || COALESCE(MAX(syncedAt), '') FROM cached_stock_items WHERE companyId = :companyId")
    suspend fun freshnessFingerprint(companyId: String): String

    @Transaction
    suspend fun replaceAllForCompany(companyId: String, entities: List<StockItemEntity>) {
        deleteForCompany(companyId)
        if (entities.isNotEmpty()) {
            upsertAll(entities)
        }
    }

    @Query(
        """
        SELECT * FROM cached_stock_items
        WHERE companyId = :companyId
          AND (
            :query IS NULL
            OR name LIKE '%' || :query || '%' COLLATE NOCASE
            OR IFNULL(alias, '') LIKE '%' || :query || '%' COLLATE NOCASE
            OR IFNULL(parentGroup, '') LIKE '%' || :query || '%' COLLATE NOCASE
            OR IFNULL(category, '') LIKE '%' || :query || '%' COLLATE NOCASE
            OR IFNULL(partNumber, '') LIKE '%' || :query || '%' COLLATE NOCASE
          )
        ORDER BY
          CASE WHEN :sortBy = 'parentGroup' AND :ascending = 1 THEN parentGroup END COLLATE NOCASE ASC,
          CASE WHEN :sortBy = 'parentGroup' AND :ascending = 0 THEN parentGroup END COLLATE NOCASE DESC,
          CASE WHEN :sortBy = 'category' AND :ascending = 1 THEN category END COLLATE NOCASE ASC,
          CASE WHEN :sortBy = 'category' AND :ascending = 0 THEN category END COLLATE NOCASE DESC,
          CASE WHEN :sortBy = 'baseUnit' AND :ascending = 1 THEN baseUnit END COLLATE NOCASE ASC,
          CASE WHEN :sortBy = 'baseUnit' AND :ascending = 0 THEN baseUnit END COLLATE NOCASE DESC,
          CASE WHEN :sortBy = 'syncedAt' AND :ascending = 1 THEN syncedAt END ASC,
          CASE WHEN :sortBy = 'syncedAt' AND :ascending = 0 THEN syncedAt END DESC,
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
    ): List<StockItemEntity>

    @Query(
        """
        SELECT COUNT(*) FROM cached_stock_items
        WHERE companyId = :companyId
          AND (
            :query IS NULL
            OR name LIKE '%' || :query || '%' COLLATE NOCASE
            OR IFNULL(alias, '') LIKE '%' || :query || '%' COLLATE NOCASE
            OR IFNULL(parentGroup, '') LIKE '%' || :query || '%' COLLATE NOCASE
            OR IFNULL(category, '') LIKE '%' || :query || '%' COLLATE NOCASE
            OR IFNULL(partNumber, '') LIKE '%' || :query || '%' COLLATE NOCASE
          )
        """,
    )
    suspend fun countMatching(companyId: String, query: String?): Int
}
