package com.budcom.android.feature.serverconfig.data.startup

import com.budcom.android.core.network.ConnectorBaseUrlProvider
import com.budcom.android.core.startup.ConnectorBaseUrlHydrator
import com.budcom.android.feature.serverconfig.data.local.ConnectorBaseUrlLocalStore
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
