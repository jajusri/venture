package com.budcom.android.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.budcom.android.BuildConfig
import com.budcom.android.core.connection.ConnectorConnectionOrchestrator
import com.budcom.android.core.connection.ConnectorReconnectCoordinator
import com.budcom.android.core.relay.data.RelayEndpointHydrator
import com.budcom.android.core.startup.ConnectorBaseUrlHydrator
import com.budcom.android.core.trust.data.TrustEndpointHydrator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Application entry point for BUDCO Android.
 *
 * Owns process-wide setup: Hilt, Timber, WorkManager, Connector base URL hydration through
 * the core [ConnectorBaseUrlHydrator] port, and identity-based Connector reconnection.
 */
@HiltAndroidApp
class BudcomApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var baseUrlHydrator: ConnectorBaseUrlHydrator

    @Inject
    lateinit var connectionOrchestrator: ConnectorConnectionOrchestrator

    @Inject
    lateinit var reconnectCoordinator: ConnectorReconnectCoordinator

    @Inject
    lateinit var trustEndpointHydrator: TrustEndpointHydrator

    @Inject
    lateinit var relayEndpointHydrator: RelayEndpointHydrator

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        plantTimber()
        hydrateThenResolveConnectionAsync()
        reconnectCoordinator.start(applicationScope)
        applicationScope.launch(Dispatchers.IO) { trustEndpointHydrator.hydrate() }
        applicationScope.launch(Dispatchers.IO) { relayEndpointHydrator.hydrate() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun plantTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    /**
     * Loads the persisted raw URL first (today's exact behavior, so any existing install
     * keeps working even if resolution below finds nothing), then attempts identity-based
     * resolution in the background. Bounded (single probe + single discovery pass) and never
     * run on the main thread, so app startup is never blocked by it.
     */
    private fun hydrateThenResolveConnectionAsync() {
        applicationScope.launch(Dispatchers.IO) {
            baseUrlHydrator.hydrate()
            runCatching { connectionOrchestrator.ensureConnected() }
                .onFailure { Timber.tag("ConnectorOrchestrator").e(it, "ensureConnected threw") }
        }
    }
}
