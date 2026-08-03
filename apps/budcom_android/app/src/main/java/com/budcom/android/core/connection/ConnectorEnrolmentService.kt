package com.budcom.android.core.connection

import com.budcom.android.core.connection.data.local.PairedConnectorEntity
import com.budcom.android.core.connection.data.local.PairedConnectorLocalDataSource
import com.budcom.android.core.discovery.ConnectorDiscoveryPort
import com.budcom.android.core.discovery.DiscoveredConnector
import com.budcom.android.core.network.ConnectorBaseUrlProvider
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.serverconfig.data.local.ConnectorBaseUrlLocalStore
import com.budcom.android.feature.serverconfig.domain.validation.ConnectorUrlValidator
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a single user-confirmed pairing attempt. */
sealed class EnrolmentResult {
    data class Paired(val connectorId: String, val friendlyName: String) : EnrolmentResult()
    data object Unreachable : EnrolmentResult()
    data class IdentityMismatch(val advertisedConnectorId: String, val actualConnectorId: String?) : EnrolmentResult()
}

/**
 * First-install Connector discovery and identity enrolment.
 *
 * Composes the existing [ConnectorDiscoveryPort] (NSD), [ConnectorHealthProbe],
 * [PairedConnectorLocalDataSource] and [ConnectorBaseUrlProvider]/[ConnectorBaseUrlLocalStore]
 * — the same primitives [ConnectorConnectionResolver] uses for ID-matched reconnection — into
 * the one-time flow that creates the first paired record a user must explicitly confirm.
 *
 * Never auto-pairs: [pair] only proceeds on a candidate the user has already selected from
 * [discover]'s results, and still independently re-verifies the endpoint's live health
 * response Connector ID against the advertised one before persisting anything.
 */
interface ConnectorEnrolmentService {
    /** A single bounded discovery pass. Never auto-selects or pairs. */
    suspend fun discover(timeoutMs: Long): List<DiscoveredConnector>

    /**
     * Health-checks [candidate], confirms its live Connector ID matches the advertised one,
     * then atomically persists the pairing and — only after that succeeds — updates the
     * live base URL. Never touches persistence or the base URL on any failure path.
     */
    suspend fun pair(candidate: DiscoveredConnector): EnrolmentResult
}

@Singleton
class DefaultConnectorEnrolmentService @Inject constructor(
    private val discoveryPort: ConnectorDiscoveryPort,
    private val healthProbe: ConnectorHealthProbe,
    private val pairedConnectors: PairedConnectorLocalDataSource,
    private val baseUrlLocalStore: ConnectorBaseUrlLocalStore,
    private val baseUrlProvider: ConnectorBaseUrlProvider,
    private val timeProvider: TimeProvider,
) : ConnectorEnrolmentService {

    override suspend fun discover(timeoutMs: Long): List<DiscoveredConnector> =
        discoveryPort.discover(timeoutMs)

    override suspend fun pair(candidate: DiscoveredConnector): EnrolmentResult {
        val probe = healthProbe.probe(candidate.host, candidate.port, HEALTH_PROBE_TIMEOUT_MS)
        if (!probe.reachable) {
            return EnrolmentResult.Unreachable
        }
        if (probe.connectorId != candidate.connectorId) {
            return EnrolmentResult.IdentityMismatch(candidate.connectorId, probe.connectorId)
        }

        val normalizedUrl = ConnectorUrlValidator.normalizeOrNull("http://${candidate.host}:${candidate.port}")
            ?: return EnrolmentResult.Unreachable

        val now = timeProvider.nowEpochMillis()
        val friendlyName = probe.connectorName ?: candidate.name
        pairedConnectors.upsert(
            PairedConnectorEntity(
                connectorId = candidate.connectorId,
                friendlyName = friendlyName,
                lastKnownHost = candidate.host,
                lastKnownPort = candidate.port,
                lastConnectedAtEpochMillis = now,
                createdAtEpochMillis = now,
            ),
        )
        baseUrlLocalStore.save(normalizedUrl)
        baseUrlProvider.updateInMemory(normalizedUrl)

        return EnrolmentResult.Paired(candidate.connectorId, friendlyName)
    }

    private companion object {
        const val HEALTH_PROBE_TIMEOUT_MS = 5_000L
    }
}
