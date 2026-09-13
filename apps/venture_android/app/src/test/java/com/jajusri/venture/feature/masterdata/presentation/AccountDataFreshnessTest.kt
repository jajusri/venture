package com.jajusri.venture.feature.masterdata.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDataFreshnessTest {
    @Test
    fun missingContentProducesNoFreshnessClaim() {
        assertNull(accountDataFreshnessLine(null, isRefreshing = false, isOnline = true, hasContent = false))
    }

    @Test
    fun unknownTimestampDoesNotClaimLive() {
        val line = accountDataFreshnessLine(null, isRefreshing = false, isOnline = true, hasContent = true)!!
        assertTrue(line.startsWith("Last successful sync: not recorded"))
        assertFalse(line.contains("Live", ignoreCase = true))
        assertFalse(line.contains("connected", ignoreCase = true))
    }

    @Test
    fun offlineSavedDataIsStatedWithoutInventingASyncTime() {
        val line = accountDataFreshnessLine(
            dataFreshnessAt = null,
            isRefreshing = false,
            isOnline = false,
            hasContent = true,
        )!!
        assertTrue(line.contains("Offline — showing saved data"))
        assertTrue(line.contains("not recorded"))
    }

    @Test
    fun refreshingIsAnExplicitUpdatingState() {
        val line = accountDataFreshnessLine("2026-08-18T09:00:00Z", isRefreshing = true, isOnline = true, hasContent = true)!!
        assertTrue(line.contains("Updating"))
        assertTrue(line.startsWith("Last successful sync:"))
        assertFalse(line.contains("Live", ignoreCase = true))
    }
}
