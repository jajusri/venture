package com.jajusri.venture.feature.transaction.data

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.util.TimeProvider
import com.jajusri.venture.feature.serverconfig.domain.port.ConnectorStatusPort
import com.jajusri.venture.feature.transaction.domain.model.TransactionClock
import com.jajusri.venture.feature.transaction.domain.model.TransactionTimestamp
import com.jajusri.venture.feature.transaction.domain.model.TransactionTimestampSource
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors [com.jajusri.venture.feature.catalogue.data.CatalogueClockImpl]'s exact resolution logic
 * and rationale (architecture §9: "every `...At`/`...AtSource` pair follows the exact same
 * two-column convention already used throughout `catalogue_*` tables") — deliberately a distinct
 * class/type rather than a shared import, since timestamp authority is a general "is our paired
 * Connector reachable" concern, not something this feature borrows from Catalogue's own module.
 *
 * Same named, deliberately unresolved limitation as Catalogue's own clock: an offline write has no
 * authoritative timestamp available by construction and gets [TransactionTimestampSource.DeviceLocalProvisional].
 */
@Singleton
class TransactionClockImpl @Inject constructor(
    private val connectorStatusPort: ConnectorStatusPort,
    private val timeProvider: TimeProvider,
) : TransactionClock {

    override suspend fun now(): TransactionTimestamp {
        val probe = withTimeoutOrNull(PROBE_TIMEOUT_MILLIS) {
            runCatching { connectorStatusPort.probeConnection() }.getOrNull()
        }
        val serverTime = (probe as? AppResult.Success)?.value?.health?.serverTimeEpochMillis
        return if (serverTime != null) {
            TransactionTimestamp(serverTime, TransactionTimestampSource.Connector)
        } else {
            TransactionTimestamp(timeProvider.nowEpochMillis(), TransactionTimestampSource.DeviceLocalProvisional)
        }
    }

    private companion object {
        const val PROBE_TIMEOUT_MILLIS = 2_500L
    }
}
