package com.jajusri.venture.core.connection

import com.jajusri.venture.core.connection.data.local.PairedConnectorLocalDataSource
import com.jajusri.venture.core.network.DefaultConnectorBaseUrlProvider
import com.jajusri.venture.core.util.DeviceEnvironment
import com.jajusri.venture.feature.serverconfig.data.local.ConnectorBaseUrlLocalStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether the app should route to first-install Connector discovery/enrolment
 * instead of the normal Dashboard.
 *
 * True only when ALL of:
 * - no Connector has ever been paired on this device;
 * - the device is not recognized as an emulator (debug emulator builds retain their development
 *   bootstrap flow);
 * - the persisted base URL is still the untouched build default — i.e. the user never
 *   supplied an explicit debug/developer configuration.
 */
interface ConnectorEnrolmentGate {
    suspend fun needsEnrolment(): Boolean
}

@Singleton
class DefaultConnectorEnrolmentGate @Inject constructor(
    private val pairedConnectors: PairedConnectorLocalDataSource,
    private val deviceEnvironment: DeviceEnvironment,
    private val baseUrlLocalStore: ConnectorBaseUrlLocalStore,
) : ConnectorEnrolmentGate {

    override suspend fun needsEnrolment(): Boolean {
        if (pairedConnectors.getPrimary() != null) return false
        if (deviceEnvironment.isLikelyEmulator()) return false
        val persisted = baseUrlLocalStore.read()
        return persisted.isBlank() || persisted == DefaultConnectorBaseUrlProvider.DEFAULT
    }
}
