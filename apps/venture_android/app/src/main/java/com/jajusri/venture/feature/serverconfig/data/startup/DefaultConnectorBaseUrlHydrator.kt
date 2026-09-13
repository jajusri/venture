package com.jajusri.venture.feature.serverconfig.data.startup

import com.jajusri.venture.core.network.ConnectorBaseUrlProvider
import com.jajusri.venture.core.startup.ConnectorBaseUrlHydrator
import com.jajusri.venture.feature.serverconfig.data.local.ConnectorBaseUrlLocalStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultConnectorBaseUrlHydrator @Inject constructor(
    private val localStore: ConnectorBaseUrlLocalStore,
    private val baseUrlProvider: ConnectorBaseUrlProvider,
) : ConnectorBaseUrlHydrator {

    override suspend fun hydrate() {
        baseUrlProvider.updateInMemory(localStore.read())
    }
}
