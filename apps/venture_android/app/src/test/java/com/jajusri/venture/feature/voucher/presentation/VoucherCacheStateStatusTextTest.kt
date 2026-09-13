package com.jajusri.venture.feature.voucher.presentation

import com.jajusri.venture.feature.voucher.domain.model.VoucherCacheState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoucherCacheStateStatusTextTest {
    @Test
    fun liveCacheDoesNotClaimLiveInUserCopy() {
        val text = VoucherCacheState.Live.statusText(1_700_000_000_000L)
        assertTrue(text.startsWith("Last successful refresh:"))
        assertFalse(text.contains("Live", ignoreCase = true))
        assertFalse(text.contains("connected", ignoreCase = true))
    }

    @Test
    fun liveWithoutTimestampDoesNotInventPrecision() {
        val text = VoucherCacheState.Live.statusText(null)
        assertTrue(text.contains("time not recorded"))
        assertFalse(text.contains("Live", ignoreCase = true))
    }

    @Test
    fun offlineStatesSavedDataWithoutUnknownPlaceholder() {
        val text = VoucherCacheState.Offline.statusText(null)
        assertTrue(text.contains("Offline — showing saved data"))
        assertTrue(text.contains("not recorded"))
        assertFalse(text.contains("unknown", ignoreCase = true))
    }
}
