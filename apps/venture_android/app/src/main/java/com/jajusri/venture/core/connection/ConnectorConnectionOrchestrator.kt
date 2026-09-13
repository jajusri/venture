package com.jajusri.venture.core.connection

import com.jajusri.venture.core.connection.data.local.PairedConnectorLocalDataSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single production entry point that makes [ConnectorConnectionResolver] and
 * [ExistingUrlMigrationService] take effect for the real app, instead of existing only as
 * unit-tested, unwired classes.
 *
 * Composes the two pieces into the one authoritative reconnection flow:
 *  - No paired Connector yet (fresh install / pre-existing raw-URL install): bridge the
 *    currently configured raw URL into a paired record exactly once
 *    ([ExistingUrlMigrationService]). This never re-runs once a paired record exists, so a
 *    second VENTURE Connector later reachable at the same raw URL can never silently replace
 *    the paired identity (requirement: no automatic switching between Connectors).
 *  - Already paired: resolve a working endpoint for that exact `connectorId`
 *    ([ConnectorConnectionResolver]) — last-known-endpoint probe, then bounded mDNS fallback.
 *
 * A single in-flight call at a time: overlapping triggers (app start racing a network-change
 * event) collapse into one bounded attempt rather than stacking concurrent resolutions.
 */
interface ConnectorConnectionOrchestrator {
    suspend fun ensureConnected(): ConnectionResolution
}

@Singleton
class DefaultConnectorConnectionOrchestrator @Inject constructor(
    private val migrationService: ExistingUrlMigrationService,
    private val resolver: ConnectorConnectionResolver,
    private val pairedConnectors: PairedConnectorLocalDataSource,
) : ConnectorConnectionOrchestrator {

    private val mutex = Mutex()

    override suspend fun ensureConnected(): ConnectionResolution = mutex.withLock {
        Timber.tag("ConnectorOrchestrator").d("ensureConnected start")
        if (pairedConnectors.getPrimary() == null) {
            val outcome = migrationService.migrateIfNeeded()
            Timber.tag("ConnectorOrchestrator").d("migration outcome=%s", outcome)
        }
        val result = resolver.resolve()
        Timber.tag("ConnectorOrchestrator").d("resolve result=%s", result)
        result
    }
}
