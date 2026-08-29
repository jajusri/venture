package com.budcom.android.core.relay

import com.budcom.android.core.relay.data.remote.DefaultRelayRuntimeEndpointProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayRuntimeEndpointProviderTest {
    @Test
    fun `defaults to unconfigured (null) in every build variant -- no BuildConfig fallback`() {
        assertNull(DefaultRelayRuntimeEndpointProvider().snapshot())
    }

    @Test
    fun `reflects the most recently hydrated or configured value`() {
        val provider = DefaultRelayRuntimeEndpointProvider()
        provider.updateInMemory("http://192.168.1.50:8082/")
        assertEquals("http://192.168.1.50:8082/", provider.snapshot())
        provider.updateInMemory(null)
        assertNull(provider.snapshot())
    }

    @Test
    fun `observe emits the current value to new collectors`() = runTest {
        val provider = DefaultRelayRuntimeEndpointProvider()
        provider.updateInMemory("http://relay.example:8082/")
        assertEquals("http://relay.example:8082/", provider.observe().first())
    }
}
