package com.jajusri.venture.feature.party.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TagDao {
    @Query("SELECT * FROM party_tags WHERE tagId = :tagId")
    suspend fun findById(tagId: String): TagEntity?

    /** Every tag in the (global, not company-scoped) tag vocabulary — used to populate an "add
     * existing tag" picker. Tag vocabularies are expected to stay small in practice; unpaged. */
    @Query("SELECT * FROM party_tags ORDER BY path COLLATE NOCASE ASC")
    suspend fun findAll(): List<TagEntity>

    @Query("SELECT * FROM party_tags WHERE name = :name AND (:parentTagId IS NULL AND parentTagId IS NULL OR parentTagId = :parentTagId)")
    suspend fun findByNameUnderParent(name: String, parentTagId: String?): TagEntity?

    @Query("SELECT * FROM party_tags WHERE parentTagId = :parentTagId ORDER BY name COLLATE NOCASE ASC")
    suspend fun findChildren(parentTagId: String?): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun assign(entity: PartyTagCrossRefEntity)

    @Query("DELETE FROM party_tag_assignments WHERE companyId = :companyId AND partyId = :partyId AND tagId = :tagId")
    suspend fun unassign(companyId: String, partyId: String, tagId: String)

    @Query(
        """
        SELECT t.* FROM party_tags t
        INNER JOIN party_tag_assignments a ON a.tagId = t.tagId
        WHERE a.companyId = :companyId AND a.partyId = :partyId
        ORDER BY t.path COLLATE NOCASE ASC
        """,
    )
    suspend fun findTagsForParty(companyId: String, partyId: String): List<TagEntity>

    @Query(
        "SELECT partyId FROM party_tag_assignments WHERE companyId = :companyId AND tagId = :tagId",
    )
    suspend fun findPartyIdsForTag(companyId: String, tagId: String): List<String>

    /** Every tag assignment for a company, joined to its tag, in one bounded query — used only by
     * Connect's list-enrichment join (grouping by partyId in Kotlin), never per-row per party. */
    @Query(
        """
        SELECT a.partyId as partyId, t.tagId as tagId, t.parentTagId as parentTagId, t.name as name, t.path as path, t.createdAt as createdAt
        FROM party_tag_assignments a
        INNER JOIN party_tags t ON t.tagId = a.tagId
        WHERE a.companyId = :companyId
        ORDER BY t.path COLLATE NOCASE ASC
        """,
    )
    suspend fun findTagsForCompany(companyId: String): List<PartyTagAssignmentRow>
}

data class PartyTagAssignmentRow(
    val partyId: String,
    val tagId: String,
    val parentTagId: String?,
    val name: String,
    val path: String,
    val createdAt: Long,
)
