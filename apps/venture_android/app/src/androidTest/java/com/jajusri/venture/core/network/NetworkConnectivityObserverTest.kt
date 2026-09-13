package com.jajusri.venture.core.network

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [DefaultNetworkConnectivityObserver] against the real device/emulator
 * [android.net.ConnectivityManager] — the framework classes it depends on
 * (`ConnectivityManager`, `NetworkCallback`, `Network`, `NetworkCapabilities`) have no JVM
 * implementation and this project uses neither Robolectric nor a mocking framework (see
 * repository convention notes elsewhere), so a plain unit test cannot exercise the actual
 * registration call. This cannot simulate a physical Wi-Fi toggle — it proves the
 * `registerDefaultNetworkCallback`/`unregisterNetworkCallback` wiring is valid Android API usage
 * that registers, emits, and tears down cleanly, not that Wi-Fi-loss delivery timing is fixed
 * (that requires the physical acceptance retest).
 */
@RunWith(AndroidJUnit4::class)
class NetworkConnectivityObserverTest {

    private fun createObserver(): DefaultNetworkConnectivityObserver {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as Context
        return DefaultNetworkConnectivityObserver(context)
    }

    @Test
    fun currentMatchesRealConnectivityManagerState() {
        val observer = createObserver()
        val connectivityManager = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val expected = capabilities != null &&
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        assertEquals(expected, observer.current())
    }

    @Test
    fun isOnlineEmitsAnInitialValueMatchingCurrentWithoutThrowing() = runBlocking {
        val observer = createObserver()

        val first = withTimeout(10_000) { observer.isOnline.first() }

        assertEquals(observer.current(), first)
    }

    @Test
    fun repeatedSubscribeAndCancelDoesNotThrowOnRegisterOrUnregister() = runBlocking {
        val observer = createObserver()

        // Registers a real NetworkCallback, collects one value, then cancels — exercising
        // awaitClose { unregisterNetworkCallback(...) } — three times in a row, proving no
        // leaked/duplicate registration crashes on a second registration attempt.
        repeat(3) {
            withTimeout(10_000) { observer.isOnline.first() }
        }
    }
}
