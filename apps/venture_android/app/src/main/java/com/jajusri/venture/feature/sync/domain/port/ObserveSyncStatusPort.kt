package com.jajusri.venture.feature.sync.domain.port

import com.jajusri.venture.feature.sync.domain.model.SyncStatusSummary
import kotlinx.coroutines.flow.StateFlow

/**
 * Stable read-only port for canonical Sync summary (Dashboard and future features).
 *
 * Values are in-process and refreshed from Connector status/runs; not a durable Room cache.
 */
interface ObserveSyncStatusPort {
    val summary: StateFlow<SyncStatusSummary>
}
