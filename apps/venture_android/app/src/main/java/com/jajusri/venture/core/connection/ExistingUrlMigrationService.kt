package com.jajusri.venture.core.connection

import com.jajusri.venture.core.connection.data.local.PairedConnectorEntity
import com.jajusri.venture.core.connection.data.local.PairedConnectorLocalDataSource
import com.jajusri.venture.core.network.ConnectorBaseUrlProvider
import com.jajusri.venture.core.util.TimeProvider
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of an [ExistingUrlMigrationService.migrateIfNeeded] attempt. */
sealed class MigrationOutcome {
    data class Migrated(val connectorId: String) : MigrationOutcome()
    data class AlreadyPaired(val connectorId: String) : MigrationOutcome()
    data object Unconfigured : MigrationOutcome()
    data object Unreachable : MigrationOutcome()
}

/**
 * Bridges pre-existing raw-URL installs ("URL is identity") onto the new stable
 * `connectorId`-based pairing model, without disturbing the raw-URL flow that already
 * works today.
 *
 * Idempotent: safe to call repeatedly (e.g. after every successful raw-URL request).
 * On failure, [ConnectorBaseUrlProvider] and the underlying
 * [com.jajusri.venture.feature.serverconfig.data.local.ConnectorBaseUrlLocalStore] value
 * are left completely untouched — the existing raw-URL config keeps working exactly as
 * today until migration succeeds.
 */
interface ExistingUrlMigrationService {
    /** Probes the currently configured base URL and records pairing if reachable. */
    suspend fun migrateIfNeeded(): MigrationOutcome
}

@Singleton
class DefaultExistingUrlMigrationService @Inject constructor(
    private val baseUrlProvider: ConnectorBaseUrlProvider,
    private val healthProbe: ConnectorHealthProbe,
    private val pairedConnectors: PairedConnectorLocalDataSource,
    private val timeProvider: TimeProvider,
) : ExistingUrlMigrationService {

    override suspend fun migrateIfNeeded(): MigrationOutcome {
        val currentUrl = baseUrlProvider.snapshotHttpUrl() ?: return MigrationOutcome.Unconfigured
        Timber.tag("ConnectorMigration").d("probing host=%s port=%s", currentUrl.host, currentUrl.port)
        val probe = healthProbe.probe(currentUrl.host, currentUrl.port, PROBE_TIMEOUT_MS)
        Timber.tag("ConnectorMigration").d(
            "probe result reachable=%s connectorId=%s connectorName=%s",
            probe.reachable,
            probe.connectorId,
            probe.connectorName,
        )
        val connectorId = probe.connectorId
        if (!probe.reachable || connectorId == null) {
            return MigrationOutcome.Unreachable
        }

        val now = timeProvider.nowEpochMillis()
        val existing = pairedConnectors.findByConnectorId(connectorId)
        if (existing != null) {
            pairedConnectors.updateLastKnownEndpoint(connectorId, currentUrl.host, currentUrl.port, now)
            return MigrationOutcome.AlreadyPaired(connectorId)
        }

        pairedConnectors.upsert(
            PairedConnectorEntity(
                connectorId = connectorId,
                friendlyName = probe.connectorName ?: DEFAULT_FRIENDLY_NAME,
                lastKnownHost = currentUrl.host,
                lastKnownPort = currentUrl.port,
                lastConnectedAtEpochMillis = now,
                createdAtEpochMillis = now,
            ),
        )
        return MigrationOutcome.Migrated(connectorId)
    }

    private companion object {
        const val PROBE_TIMEOUT_MS = 3_000L
        const val DEFAULT_FRIENDLY_NAME = "Venture Connector"
    }
}
