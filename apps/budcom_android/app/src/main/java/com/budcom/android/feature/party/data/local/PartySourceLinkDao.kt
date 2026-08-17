package com.budcom.android.feature.party.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PartySourceLinkDao {
    /**
     * The critical identity-resolution lookup: given an external key (company + source type +
     * stable external id, e.g. a Tally ledger's `guid:`/`name:`-prefixed id), find the Party it
     * already maps to, if any. A rename never changes [externalEntityId], so this lookup is what
     * guarantees a renamed ledger keeps its existing `partyId` instead of minting a new Party.
     */
    @Query(
        "SELECT * FROM party_source_links " +
            "WHERE companyId = :companyId AND sourceType = :sourceType AND externalEntityId = :externalEntityId",
    )
    suspend fun findByExternalKey(companyId: String, sourceType: String, externalEntityId: String): PartySourceLinkEntity?

    @Query("SELECT * FROM party_source_links WHERE companyId = :companyId AND partyId = :partyId")
    suspend fun findByPartyId(companyId: String, partyId: String): List<PartySourceLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PartySourceLinkEntity)
}
