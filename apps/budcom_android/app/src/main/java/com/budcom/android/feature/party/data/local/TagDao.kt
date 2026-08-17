package com.budcom.android.feature.party.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TagDao {
    @Query("SELECT * FROM party_tags WHERE tagId = :tagId")
    suspend fun findById(tagId: String): TagEntity?

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
}
