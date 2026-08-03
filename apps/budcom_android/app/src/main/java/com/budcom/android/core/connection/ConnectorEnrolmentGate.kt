package com.budcom.android.core.connection

import com.budcom.android.core.connection.data.local.PairedConnectorLocalDataSource
import com.budcom.android.core.network.DefaultConnectorBaseUrlProvider
import com.budcom.android.core.util.DeviceEnvironment
import com.budcom.android.feature.serverconfig.data.local.ConnectorBaseUrlLocalStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether the app should route to first-install Connector discovery/enrolment
 * instead of the normal Dashboard.
 *
 * True only when ALL of:
 * - no Connector has ever been paired on this device;
 * - the device is not recognized as an emulator (emulator dev flow keeps using
 *   [com.budcom.android.core.connection.ExistingUrlMigrationService] against the
 *   `10.0.2.2` default exactly as before);
 * - the persisted base URL is still the untouched build default — i.e. the user never
 *   went through Server Config manually (an explicit developer configuration is honored
 *   exactly as before, never redirected into the discovery flow).
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
        return baseUrlLocalStore.read() == DefaultConnectorBaseUrlProvider.DEFAULT
    }
}
