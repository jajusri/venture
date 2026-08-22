package com.budcom.android.feature.sync.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDtoMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun progressDeserializesNullableTotal() {
        val dto = json.decodeFromString(
            SyncProgressDto.serializer(),
            """
            {
              "syncRunId":"r1",
              "status":"running",
              "totalExpected":null,
              "startedAt":"2026-07-27T00:00:00.000Z",
              "completedAt":null,
              "durationMs":null,
              "itemsProcessed":3,
              "itemsAdded":1,
              "itemsUpdated":1,
              "itemsSkipped":1,
              "itemsFailed":0,
              "lastError":null,
              "cancelRequested":false,
              "storageBackend":"sqlite",
              "migrationStatus":"none"
            }
            """.trimIndent(),
        )
        val progress = dto.toDomain()
        assertEquals("r1", progress.syncRunId)
        assertNull(progress.counts.totalExpected)
        assertEquals(3, progress.counts.itemsProcessed)
        assertTrue(!progress.counts.hasDeterminateProgress)
    }

    @Test
    fun ledgerResultMapsCompletedWithWarnings() {
        val dto = json.decodeFromString(
            LedgerSyncResultDto.serializer(),
            """
            {
              "schemaVersion":"1.0.0",
              "syncRunId":"r2",
              "status":"completed",
              "statistics":{"totalLedgers":10,"activeLedgers":8,"inactiveLedgers":2,"reservedLedgers":0,"deletedLedgers":0,"withGst":0,"withOpeningBalance":0,"lastSyncedAt":"2026-07-27T01:00:00.000Z"},
              "progress":{"syncRunId":"r2","status":"completed","totalExpected":10,"startedAt":"t0","completedAt":"t1","durationMs":100,"itemsProcessed":10,"itemsAdded":2,"itemsUpdated":3,"itemsSkipped":5,"itemsFailed":0,"lastError":null,"cancelRequested":false,"storageBackend":"sqlite","migrationStatus":"none"},
              "changes":[],
              "validationIssueCount":2
            }
            """.trimIndent(),
        )
        val outcome = dto.toOutcome() as com.budcom.android.feature.sync.domain.model.SyncOutcome.Succeeded
        assertEquals(2, outcome.validationIssueCount)
        assertTrue(outcome.warningMessage!!.contains("2"))
    }

    @Test
    fun statusResponseCarriesSchedulerStateIntoTheDomainProgress() {
        val dto = json.decodeFromString(
            SyncStatusResponseDto.serializer(),
            """
            {
              "schemaVersion":"1.0.0",
              "progress":{"syncRunId":"r1","status":"idle","totalExpected":null,"startedAt":null,"completedAt":null,"durationMs":null,"itemsProcessed":0,"itemsAdded":0,"itemsUpdated":0,"itemsSkipped":0,"itemsFailed":0,"lastError":null,"cancelRequested":false},
              "schedulerState":{"stage":"active_window","nextCheckDueAt":"2026-08-22T10:05:00.000Z"}
            }
            """.trimIndent(),
        )
        val progress = dto.progress.toDomain(dto.schedulerState?.toDomain())
        assertEquals(com.budcom.android.feature.sync.domain.model.SchedulerStage.ActiveWindow, progress.schedulerState?.stage)
        assertEquals("2026-08-22T10:05:00.000Z", progress.schedulerState?.nextCheckDueAt)
    }

    @Test
    fun statusResponseWithoutSchedulerStateMapsToNullGracefully() {
        val dto = json.decodeFromString(
            SyncStatusResponseDto.serializer(),
            """
            {
              "schemaVersion":"1.0.0",
              "progress":{"syncRunId":"r1","status":"idle","totalExpected":null,"startedAt":null,"completedAt":null,"durationMs":null,"itemsProcessed":0,"itemsAdded":0,"itemsUpdated":0,"itemsSkipped":0,"itemsFailed":0,"lastError":null,"cancelRequested":false}
            }
            """.trimIndent(),
        )
        val progress = dto.progress.toDomain(dto.schedulerState?.toDomain())
        assertNull(progress.schedulerState)
    }

    @Test
    fun unknownSchedulerStageStringMapsToUnknownRatherThanCrashing() {
        val dto = json.decodeFromString(
            SchedulerStateDto.serializer(),
            """{"stage":"some_future_stage","nextCheckDueAt":null}""",
        )
        assertEquals(com.budcom.android.feature.sync.domain.model.SchedulerStage.Unknown, dto.toDomain().stage)
    }

    @Test
    fun stockPartialMapsPartialSuccess() {
        val dto = json.decodeFromString(
            StockSyncResultDto.serializer(),
            """
            {
              "schemaVersion":"1.0.0",
              "syncRunId":"r3",
              "status":"completed",
              "extractionCompleteness":"partial",
              "deletionReconciliation":"disabled",
              "statistics":{"totalStockItems":4,"withBaseUnit":4,"incompleteData":0,"withHsn":0,"withGst":0,"withOpeningBalance":0,"deletedStockItems":0,"lastSyncedAt":null},
              "progress":{"syncRunId":"r3","status":"completed","totalExpected":4,"startedAt":"t0","completedAt":"t1","durationMs":50,"itemsProcessed":4,"itemsAdded":4,"itemsUpdated":0,"itemsSkipped":0,"itemsFailed":0,"lastError":null,"cancelRequested":false,"storageBackend":"sqlite","migrationStatus":"none"},
              "changes":[],
              "validationIssueCount":0
            }
            """.trimIndent(),
        )
        val outcome = dto.toOutcome()
        assertTrue(outcome is com.budcom.android.feature.sync.domain.model.SyncOutcome.PartiallySucceeded)
    }
}
