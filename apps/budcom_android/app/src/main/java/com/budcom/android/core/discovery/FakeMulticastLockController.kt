package com.budcom.android.core.discovery

/** Deterministic test double — records call order/counts, never touches a real WifiManager. */
class FakeMulticastLockController : MulticastLockController {

    val events = mutableListOf<String>()

    var isHeld: Boolean = false
        private set

    override fun acquire() {
        isHeld = true
        events += "acquire"
    }

    override fun release() {
        isHeld = false
        events += "release"
    }
}
