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
          AND (
            displayName LIKE '%' || :query || '%' COLLATE NOCASE
            OR IFNULL(primaryPhoneNormalized, '') LIKE '%' || :query || '%'
          )
        ORDER BY displayName COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun search(companyId: String, query: String, limit: Int, offset: Int): List<PartyEntity>

    @Query(
        """
        SELECT COUNT(*) FROM cached_parties
        WHERE companyId = :companyId
          AND (
            displayName LIKE '%' || :query || '%' COLLATE NOCASE
            OR IFNULL(primaryPhoneNormalized, '') LIKE '%' || :query || '%'
          )
        """,
    )
    suspend fun countSearch(companyId: String, query: String): Int
}
