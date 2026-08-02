package com.budcom.android.core.connection.data.local

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistence port for paired Connector identity records.
 *
 * Kept separate from [PairedConnectorDao] so domain/resolver code stays unit-testable
 * with a [com.budcom.android.core.connection.data.local.PairedConnectorLocalDataSource]
 * fake, without requiring a real Room database (Room DAOs need instrumentation to run).
 */
interface PairedConnectorLocalDataSource {
    suspend fun findByConnectorId(connectorId: String): PairedConnectorEntity?

    /** Most recently connected paired Connector, if any device has ever been paired. */
    suspend fun getPrimary(): PairedConnectorEntity?

    /** Idempotent — inserts a new row or replaces the existing row for [entity.connectorId]. */
    suspend fun upsert(entity: PairedConnectorEntity)

    suspend fun updateLastKnownEndpoint(
        connectorId: String,
        host: String,
        port: Int,
        connectedAtEpochMillis: Long,
    )
}

@Singleton
class RoomPairedConnectorLocalDataSource @Inject constructor(
    private val dao: PairedConnectorDao,
) : PairedConnectorLocalDataSource {

    override suspend fun findByConnectorId(connectorId: String): PairedConnectorEntity? =
        dao.getByConnectorId(connectorId)

    override suspend fun getPrimary(): PairedConnectorEntity? = dao.getMostRecentlyConnected()

    override suspend fun upsert(entity: PairedConnectorEntity) = dao.upsert(entity)

    override suspend fun updateLastKnownEndpoint(
        connectorId: String,
        host: String,
        port: Int,
        connectedAtEpochMillis: Long,
    ) = dao.updateLastKnownEndpoint(connectorId, host, port, connectedAtEpochMillis)
}
