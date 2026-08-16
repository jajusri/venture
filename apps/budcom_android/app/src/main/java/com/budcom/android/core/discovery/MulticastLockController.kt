package com.budcom.android.core.discovery

import android.content.Context
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin seam over [WifiManager.MulticastLock] so mDNS/NSD discovery can be unit-tested without a
 * real [android.net.wifi.WifiManager].
 *
 * Several OEM Wi-Fi stacks (BBK/vivo/OPPO/OnePlus family devices among them) filter multicast
 * frames at the driver/firmware level to save power unless some app on the device currently holds
 * a multicast lock — [android.net.nsd.NsdManager] does not acquire one on the app's behalf on
 * these builds, so `discoverServices()` can report success and simply never receive any
 * `_budcom._tcp` announcement, indistinguishable from "nothing on the network" at this API. The
 * app already declares `CHANGE_WIFI_MULTICAST_STATE` in the manifest for exactly this reason.
 */
interface MulticastLockController {
    fun acquire()
    fun release()
}

@Singleton
class WifiMulticastLockController @Inject constructor(
    @ApplicationContext context: Context,
) : MulticastLockController {

    private val lock = (context.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createMulticastLock("budcom-nsd-discovery")
        .apply { setReferenceCounted(true) }

    override fun acquire() {
        lock.acquire()
    }

    override fun release() {
        if (lock.isHeld) {
            lock.release()
        }
    }
}

/**
 * Runs [block] with the multicast lock held, always releasing it afterward — success, failure, or
 * cancellation alike. Declared `inline` so a `return` inside [block] (as [NsdConnectorDiscoveryService]
 * relies on for its early-exit path) works as an ordinary non-local return.
 */
suspend inline fun <T> MulticastLockController.withLock(block: () -> T): T {
    acquire()
    try {
        return block()
    } finally {
        release()
    }
}
