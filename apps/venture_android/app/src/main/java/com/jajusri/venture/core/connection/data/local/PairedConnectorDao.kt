package com.jajusri.venture.core.connection.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PairedConnectorDao {
    @Query("SELECT * FROM paired_connectors WHERE connectorId = :connectorId LIMIT 1")
    suspend fun getByConnectorId(connectorId: String): PairedConnectorEntity?

    @Query("SELECT * FROM paired_connectors ORDER BY lastConnectedAtEpochMillis DESC LIMIT 1")
    suspend fun getMostRecentlyConnected(): PairedConnectorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PairedConnectorEntity)

    @Query(
        "UPDATE paired_connectors SET lastKnownHost = :host, lastKnownPort = :port, " +
            "lastConnectedAtEpochMillis = :connectedAtEpochMillis WHERE connectorId = :connectorId",
    )
    suspend fun updateLastKnownEndpoint(
        connectorId: String,
        host: String,
        port: Int,
        connectedAtEpochMillis: Long,
    )
}
