package com.budcom.android.feature.company.data.remote

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.budcom.android.core.connectorauth.domain.AuthenticatedRepositoryFailurePolicy
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResponsePayload
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedCompanyRemoteDataSourceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `fetchCompanies decodes a successful payload into the domain snapshot`() = runTest {
        val port = FakePort(
            result = AuthenticatedConnectorResult.Success(
                AuthenticatedConnectorResponsePayload(
                    """{"items":[{"id":"budcom-test-01","name":"Budcom-Test-01"}],"schemaVersion":"1.0.0",""" +
                        """"dataFreshnessAt":"2026-01-01T00:00:00Z","contractVersion":"1","status":"SUCCESS","tallyReachable":true}""",
                ),
            ),
        )
        val dataSource = DefaultAuthenticatedCompanyRemoteDataSource(port, PassthroughFailurePolicy(), json)

        val result = dataSource.fetchCompanies()

        assertTrue(result is AppResult.Success)
        assertEquals("budcom-test-01", (result as AppResult.Success).value.items.single().id)
        assertEquals(AuthenticatedConnectorOperation.GetCompanies, port.lastOperation)
    }

    @Test
    fun `fetchCompanies maps malformed JSON to a Serialization failure without calling the failure policy`() = runTest {
        val port = FakePort(result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("not json")))
        val policy = PassthroughFailurePolicy()
        val dataSource = DefaultAuthenticatedCompanyRemoteDataSource(port, policy, json)

        val result = dataSource.fetchCompanies()

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Serialization)
        assertEquals(0, policy.callCount)
    }

    @Test
    fun `fetchCompanies routes a non-Success outcome through the failure policy`() = runTest {
        val port = FakePort(result = AuthenticatedConnectorResult.Unauthorized("cred-1"))
        val policy = PassthroughFailurePolicy()
        val dataSource = DefaultAuthenticatedCompanyRemoteDataSource(port, policy, json)

        val result = dataSource.fetchCompanies()

        assertTrue(result is AppResult.Failure)
        assertEquals(1, policy.callCount)
        assertEquals(AuthenticatedConnectorResult.Unauthorized("cred-1"), policy.lastResult)
    }

    @Test
    fun `selectCompany constructs the operation with the given companyId and a 200 status`() = runTest {
        val port = FakePort(
            result = AuthenticatedConnectorResult.Success(
                AuthenticatedConnectorResponsePayload(
                    """{"status":"SUCCESS","session":{"sessionId":"s1","connectionStatus":"connected",""" +
                        """"connectorVersion":"0.4.0","erpType":"tally","createdAt":"2026-01-01T00:00:00Z"}}""",
                ),
            ),
        )
        val dataSource = DefaultAuthenticatedCompanyRemoteDataSource(port, PassthroughFailurePolicy(), json)

        val result = dataSource.selectCompany("estimation") as AppResult.Success

        assertEquals("SUCCESS", result.value.status)
        assertEquals(200, result.value.httpStatus)
        assertEquals(AuthenticatedConnectorOperation.SelectCompany("estimation"), port.lastOperation)
    }

    @Test
    fun `fetchSession decodes the session envelope`() = runTest {
        val port = FakePort(
            result = AuthenticatedConnectorResult.Success(
                AuthenticatedConnectorResponsePayload(
                    """{"session":{"sessionId":"s1","connectionStatus":"connected","connectorVersion":"0.4.0",""" +
                        """"erpType":"tally","createdAt":"2026-01-01T00:00:00Z"},"contractVersion":"1"}""",
                ),
            ),
        )
        val dataSource = DefaultAuthenticatedCompanyRemoteDataSource(port, PassthroughFailurePolicy(), json)

        val result = dataSource.fetchSession() as AppResult.Success

        assertEquals("s1", result.value.sessionId)
        assertEquals(AuthenticatedConnectorOperation.GetSession, port.lastOperation)
    }

    @Test
    fun `clearSession decodes the response and reads the contract version from the body`() = runTest {
        val port = FakePort(
            result = AuthenticatedConnectorResult.Success(
                AuthenticatedConnectorResponsePayload(
                    """{"status":"SUCCESS","session":{"sessionId":"s1","connectionStatus":"connected",""" +
                        """"connectorVersion":"0.4.0","erpType":"tally","createdAt":"2026-01-01T00:00:00Z"},"contractVersion":"2"}""",
                ),
            ),
        )
        val dataSource = DefaultAuthenticatedCompanyRemoteDataSource(port, PassthroughFailurePolicy(), json)

        val result = dataSource.clearSession() as AppResult.Success

        assertEquals("2", result.value.contractVersion)
        assertEquals(AuthenticatedConnectorOperation.ClearSessionCompany, port.lastOperation)
    }

    @Test
    fun `validateSession decodes status reason and company fields`() = runTest {
        val port = FakePort(
            result = AuthenticatedConnectorResult.Success(
                AuthenticatedConnectorResponsePayload(
                    """{"status":"INVALID","session":{"sessionId":"s1","connectionStatus":"connected",""" +
                        """"connectorVersion":"0.4.0","erpType":"tally","createdAt":"2026-01-01T00:00:00Z"},""" +
                        """"reason":"invalid","companyId":"estimation","companyName":"ESTIMATION"}""",
                ),
            ),
        )
        val dataSource = DefaultAuthenticatedCompanyRemoteDataSource(port, PassthroughFailurePolicy(), json)

        val result = dataSource.validateSession() as AppResult.Success

        assertEquals("INVALID", result.value.status)
        assertEquals("estimation", result.value.companyId)
        assertEquals(AuthenticatedConnectorOperation.ValidateSession, port.lastOperation)
    }
}

private class FakePort(private val result: AuthenticatedConnectorResult) : AuthenticatedConnectorApiPort {
    var lastOperation: AuthenticatedConnectorOperation? = null
        private set

    override suspend fun execute(operation: AuthenticatedConnectorOperation): AuthenticatedConnectorResult {
        lastOperation = operation
        return result
    }
}

private class PassthroughFailurePolicy : AuthenticatedRepositoryFailurePolicy {
    var callCount = 0
        private set
    var lastResult: AuthenticatedConnectorResult? = null
        private set

    override suspend fun mapFailure(result: AuthenticatedConnectorResult): AppError {
        callCount++
        lastResult = result
        return AppError.Message("mapped: $result")
    }
}
