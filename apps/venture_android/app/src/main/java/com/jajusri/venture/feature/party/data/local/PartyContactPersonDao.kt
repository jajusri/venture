package com.jajusri.venture.feature.party.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PartyContactPersonDao {
    @Query(
        "SELECT * FROM party_contact_persons WHERE companyId = :companyId AND partyId = :partyId " +
            "ORDER BY isPrimary DESC, name COLLATE NOCASE ASC",
    )
    suspend fun findAllForParty(companyId: String, partyId: String): List<PartyContactPersonEntity>

    @Query(
        "SELECT * FROM party_contact_persons WHERE companyId = :companyId AND contactPersonId = :contactPersonId",
    )
    suspend fun findById(companyId: String, contactPersonId: String): PartyContactPersonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PartyContactPersonEntity)

    @Query("DELETE FROM party_contact_persons WHERE companyId = :companyId AND contactPersonId = :contactPersonId")
    suspend fun delete(companyId: String, contactPersonId: String)
}
