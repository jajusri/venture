package com.budcom.android.feature.party.data.local

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
}
