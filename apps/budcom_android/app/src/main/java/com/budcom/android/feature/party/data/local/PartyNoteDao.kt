package com.budcom.android.feature.party.data.local

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
}
