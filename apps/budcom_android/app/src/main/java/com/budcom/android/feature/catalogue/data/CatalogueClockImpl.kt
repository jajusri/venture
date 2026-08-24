package com.budcom.android.feature.catalogue.data

import com.budcom.android.core.common.AppResult
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestamp
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestampSource
import com.budcom.android.feature.catalogue.domain.port.CatalogueClock
import com.budcom.android.feature.serverconfig.domain.port.ConnectorStatusPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reuses the existing, stable [ConnectorStatusPort.probeConnection] (already used by
 * ServerConfig/Diagnostics) rather than adding a new Connector route — architecture §19 requires
 * no new Connector API surface for Catalogue itself. `GET /health` was extended with a
 * `serverTimeEpochMillis` field (Connector `HealthReport`/`health.ts`, additive, no Tally
 * involvement) specifically so this clock has an authoritative reading to reuse.
 *
 * On a successful probe, the Connector's own reading is authoritative for this call (architecture
 * §15: "the Connector's own clock is the authoritative source" while reachable). When the probe
 * fails (offline, or an older Connector build predating this field), falls back to the device's
 * own clock, explicitly tagged [CatalogueTimestampSource.DeviceLocalProvisional] — visible and
 * inspectable everywhere a [CatalogueTimestamp] is stored, never silently treated as equally
 * authoritative.
 *
 * **Named, deliberately unresolved limitation (architecture §15/§22 item 2):** this only resolves
 * the case where Android can reach its paired Connector *at the moment of the write*. An offline
 * Draft edit — no Connector reachable at all — has no authoritative timestamp available by
 * construction; it gets [CatalogueTimestampSource.DeviceLocalProvisional] and stays that way. The
 * architecture document explicitly declines to invent multi-device offline-conflict mechanics
 * beyond this, and this implementation does not either.
 */
@Singleton
class CatalogueClockImpl @Inject constructor(
    private val connectorStatusPort: ConnectorStatusPort,
    private val timeProvider: TimeProvider,
) : CatalogueClock {

    override suspend fun now(): CatalogueTimestamp {
        val probe = runCatching { connectorStatusPort.probeConnection() }.getOrNull()
        val serverTime = (probe as? AppResult.Success)?.value?.health?.serverTimeEpochMillis
        return if (serverTime != null) {
            CatalogueTimestamp(serverTime, CatalogueTimestampSource.Connector)
        } else {
            CatalogueTimestamp(timeProvider.nowEpochMillis(), CatalogueTimestampSource.DeviceLocalProvisional)
        }
    }
}
