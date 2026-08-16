package com.budcom.android.core.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MulticastLockControllerTest {

    @Test
    fun `acquires before running the block and releases after it returns successfully`() = runTest {
        val lock = FakeMulticastLockController()

        val result = lock.withLock { "value" }

        assertEquals("value", result)
        assertEquals(listOf("acquire", "release"), lock.events)
        assertFalse(lock.isHeld)
    }

    @Test
    fun `releases even when the block throws`() = runTest {
        val lock = FakeMulticastLockController()

        val thrown = runCatching {
            lock.withLock<Unit> { throw IllegalStateException("boom") }
        }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
        assertEquals(listOf("acquire", "release"), lock.events)
        assertFalse(lock.isHeld)
    }

    @Test
    fun `releases even when the block is cancelled`() = runTest {
        val lock = FakeMulticastLockController()

        val thrown = runCatching {
            lock.withLock<Unit> { throw CancellationException("cancelled") }
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertEquals(listOf("acquire", "release"), lock.events)
        assertFalse(lock.isHeld)
    }

    @Test
    fun `a non-local return from within the block still releases the lock`() = runTest {
        val lock = FakeMulticastLockController()

        suspend fun earlyExit(): String {
            lock.withLock {
                return "early"
            }
        }

        assertEquals("early", earlyExit())
        assertEquals(listOf("acquire", "release"), lock.events)
    }
}
