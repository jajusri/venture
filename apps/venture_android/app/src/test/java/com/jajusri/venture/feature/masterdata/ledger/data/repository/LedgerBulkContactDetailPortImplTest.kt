package com.jajusri.venture.feature.masterdata.ledger.data.repository

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.network.ApiResult
import com.jajusri.venture.core.network.DefaultErrorMapper
import com.jajusri.venture.core.network.NetworkConnectivityObserver
import com.jajusri.venture.core.network.NetworkError
import com.jajusri.venture.feature.masterdata.ledger.data.remote.LedgerRemoteDataSource
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerContactDetails
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerContactDetailsBulkResult
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerPage
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerQuery
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatement
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatementDateRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerBulkContactDetailPortImplTest {
    private val connectivity = object : NetworkConnectivityObserver {
        override val isOnline: Flow<Boolean> = MutableStateFlow(true)
        override fun current(): Boolean = true
    }
    private val errorMapper = DefaultErrorMapper(Json { ignoreUnknownKeys = true }, connectivity)

    @Test
    fun `maps a successful bulk fetch to AppResult Success`() = runTest {
        val remote = BulkContactFakeRemote(
            result = ApiResult.Success(
                LedgerContactDetailsBulkResult(
                    items = listOf(
                        LedgerContactDetails(
                            ledgerId = "guid:abc",
                            mobile = "9876543210",
                            email = "accounts@acme.example",
                            address = null,
                            state = null,
                            pincode = null,
                            gstin = "29AABCU9603R1ZM",
                        ),
                    ),
                    ledgerCount = 1,
                    durationMs = 5,
                ),
            ),
        )
        val port = LedgerBulkContactDetailPortImpl(remoteDataSource = remote, errorMapper = errorMapper)

        val result = port.fetchContactDetailsBulk() as AppResult.Success

        assertEquals(1, result.value.ledgerCount)
        assertEquals("guid:abc", result.value.items.single().ledgerId)
        assertEquals(1, remote.callCount)
    }

    @Test
    fun `maps a transport failure to AppResult Failure`() = runTest {
        val remote = BulkContactFakeRemote(result = ApiResult.Failure(NetworkError.NoConnectivity))
        val port = LedgerBulkContactDetailPortImpl(remoteDataSource = remote, errorMapper = errorMapper)

        val result = port.fetchContactDetailsBulk()

        assertTrue(result is AppResult.Failure)
    }
}

private class BulkContactFakeRemote(private val result: ApiResult<LedgerContactDetailsBulkResult>) : LedgerRemoteDataSource {
    var callCount = 0
        private set

    override suspend fun fetchLedgers(query: LedgerQuery): ApiResult<LedgerPage> = error("unused")

    override suspend fun fetchLedgerStatement(ledgerId: String, range: LedgerStatementDateRange): ApiResult<LedgerStatement> =
        error("unused")

    override suspend fun fetchLedgerContactDetails(ledgerId: String): ApiResult<LedgerContactDetails> = error("unused")

    override suspend fun fetchLedgerContactDetailsBulk(): ApiResult<LedgerContactDetailsBulkResult> {
        callCount++
        return result
    }
}
