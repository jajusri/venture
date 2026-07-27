package com.budcom.android.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.budcom.android.BuildConfig
import com.budcom.android.core.startup.ConnectorBaseUrlHydrator
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
 * Owns process-wide setup: Hilt, Timber, WorkManager, and Connector base URL hydration
 * through the core [ConnectorBaseUrlHydrator] port (never feature data types).
 */
@HiltAndroidApp
class BudcomApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var baseUrlHydrator: ConnectorBaseUrlHydrator

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        plantTimber()
        hydrateBaseUrlAsync()
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

    private fun hydrateBaseUrlAsync() {
        applicationScope.launch(Dispatchers.IO) {
            baseUrlHydrator.hydrate()
        }
    }
}
