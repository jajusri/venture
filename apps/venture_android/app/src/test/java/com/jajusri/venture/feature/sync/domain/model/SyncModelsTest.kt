package com.jajusri.venture.feature.sync.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncModelsTest {

    @Test
    fun `checkingFrequencyLabel is null when nothing is known yet`() {
        assertNull(checkingFrequencyLabel(null, null))
        assertNull(checkingFrequencyLabel())
    }

    @Test
    fun `checkingFrequencyLabel reports regularly when any known state is in the active window`() {
        val active = SchedulerState(SchedulerStage.ActiveWindow, "t")
        val backoff = SchedulerState(SchedulerStage.Backoff60, "t")
        assertEquals("Checking regularly", checkingFrequencyLabel(active, backoff))
        assertEquals("Checking regularly", checkingFrequencyLabel(null, active))
    }

    @Test
    fun `checkingFrequencyLabel reports occasionally when every known state has backed off`() {
        val backoff15 = SchedulerState(SchedulerStage.Backoff15, "t")
        val backoff30 = SchedulerState(SchedulerStage.Backoff30, "t")
        assertEquals("Checking occasionally", checkingFrequencyLabel(backoff15, backoff30))
        assertEquals("Checking occasionally", checkingFrequencyLabel(null, backoff30))
    }

    @Test
    fun `toSchedulerStage maps every known Connector stage and falls back to Unknown`() {
        assertEquals(SchedulerStage.ActiveWindow, "active_window".toSchedulerStage())
        assertEquals(SchedulerStage.Backoff15, "backoff_15".toSchedulerStage())
        assertEquals(SchedulerStage.Backoff30, "backoff_30".toSchedulerStage())
        assertEquals(SchedulerStage.Backoff60, "backoff_60".toSchedulerStage())
        assertEquals(SchedulerStage.Unknown, "something_new".toSchedulerStage())
    }
}
