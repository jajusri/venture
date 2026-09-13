package com.jajusri.venture.feature.party.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PartyExportEventDao {
    @Query(
        "SELECT * FROM party_export_events WHERE companyId = :companyId AND partyId = :partyId " +
            "ORDER BY createdAt DESC LIMIT :limit",
    )
    suspend fun recentForParty(companyId: String, partyId: String, limit: Int): List<PartyExportEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PartyExportEventEntity)
}
