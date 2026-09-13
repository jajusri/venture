package com.jajusri.venture.feature.transaction.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(
    tableName = "authenticated_counterparty_binding",
    primaryKeys = ["localBusinessId", "partyId"],
)
data class AuthenticatedCounterpartyBindingEntity(
    val localBusinessId: String,
    val partyId: String,
    val counterpartyBusinessId: String,
    val verifiedActorId: String,
    val verifiedDeviceId: String,
    val authorityEpoch: Long,
    val verificationReference: String,
    val status: String,
    val verifiedAtEpochMillis: Long,
)

@Dao
interface AuthenticatedCounterpartyBindingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AuthenticatedCounterpartyBindingEntity)

    @Query("SELECT * FROM authenticated_counterparty_binding WHERE localBusinessId = :localBusinessId AND partyId = :partyId")
    suspend fun find(localBusinessId: String, partyId: String): AuthenticatedCounterpartyBindingEntity?

    @Query("UPDATE authenticated_counterparty_binding SET status = 'REVOKED' WHERE localBusinessId = :localBusinessId AND partyId = :partyId")
    suspend fun revoke(localBusinessId: String, partyId: String)
}
