package com.budcom.android.feature.catalogue.data

import com.budcom.android.core.common.AppResult
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestamp
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestampSource
import com.budcom.android.feature.catalogue.domain.port.CatalogueClock
import com.budcom.android.feature.serverconfig.domain.port.ConnectorStatusPort
import kotlinx.coroutines.withTimeoutOrNull
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
 *
 * **Bounded wait, found live on a real device (2026-08-24):** `probeConnection()`'s underlying
 * `fetchHealth()` is wrapped in this app's shared [com.budcom.android.core.network.RetryPolicy],
 * which retries a failed connection with a real per-attempt timeout — on a real device with no
 * reachable Connector this took ~45s (three ~15s timeouts) before falling back, during which the
 * Catalogue list screen's initial load (which calls [now] for its reconciliation sweep) appeared
 * hung. Every Catalogue write calls [now], so this would have made the entire feature feel broken
 * whenever the paired Connector is merely out of Wi-Fi range — an ordinary, common state, not an
 * edge case. [withTimeoutOrNull] caps the *caller's* wait independently of that shared retry
 * policy (cancelling the coroutine aborts the in-flight OkHttp call rather than waiting it out) —
 * deliberately short, because this is a timestamp lookup a user is implicitly waiting on
 * synchronously, not a background sync.
 */
@Singleton
class CatalogueClockImpl @Inject constructor(
    private val connectorStatusPort: ConnectorStatusPort,
    private val timeProvider: TimeProvider,
) : CatalogueClock {

    override suspend fun now(): CatalogueTimestamp {
        val probe = withTimeoutOrNull(PROBE_TIMEOUT_MILLIS) {
            runCatching { connectorStatusPort.probeConnection() }.getOrNull()
        }
        val serverTime = (probe as? AppResult.Success)?.value?.health?.serverTimeEpochMillis
        return if (serverTime != null) {
            CatalogueTimestamp(serverTime, CatalogueTimestampSource.Connector)
        } else {
            CatalogueTimestamp(timeProvider.nowEpochMillis(), CatalogueTimestampSource.DeviceLocalProvisional)
        }
    }

    private companion object {
        const val PROBE_TIMEOUT_MILLIS = 2_500L
    }
}
