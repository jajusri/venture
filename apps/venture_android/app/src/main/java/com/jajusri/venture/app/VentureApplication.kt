package com.jajusri.venture.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.jajusri.venture.BuildConfig
import com.jajusri.venture.core.connection.ConnectorConnectionOrchestrator
import com.jajusri.venture.core.connection.ConnectorReconnectCoordinator
import com.jajusri.venture.core.relay.data.RelayEndpointHydrator
import com.jajusri.venture.core.startup.ConnectorBaseUrlHydrator
import com.jajusri.venture.core.trust.data.TrustEndpointHydrator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Application entry point for VENTURE Android.
 *
 * Owns process-wide setup: Hilt, Timber, WorkManager, Connector base URL hydration through
 * the core [ConnectorBaseUrlHydrator] port, and identity-based Connector reconnection.
 */
@HiltAndroidApp
class VentureApplication : Application(), Configuration.Provider {

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
