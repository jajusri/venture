package com.budcom.android.core.startup

/**
 * Loads the persisted Connector base URL into the in-memory provider at process start.
 *
 * Lives in `core` so [android.app.Application] never depends on feature data types.
 */
interface ConnectorBaseUrlHydrator {
    /**
     * Reads the saved URL (or build default when none) and updates [com.budcom.android.core.network.ConnectorBaseUrlProvider].
     * Must be called from a background dispatcher.
     */
    suspend fun hydrate()
}
