package com.jajusri.venture.core.network

import com.jajusri.venture.core.common.AppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random

class ErrorMapperTest {

    private val onlineObserver = FakeConnectivityObserver(online = true)
    private val offlineObserver = FakeConnectivityObserver(online = false)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `maps connector error body from HttpException`() {
        val mapper = DefaultErrorMapper(json, onlineObserver)
        val body = """{"code":"VALIDATION_ERROR","message":"Voucher request validation failed.","details":{"field":"company"}}"""
        val error = mapper.toNetworkError(httpException(400, body))

        val http = error as NetworkError.Http
        assertEquals(400, http.httpStatus)
        assertEquals("VALIDATION_ERROR", http.code)
        assertEquals("Voucher request validation failed.", http.message)
        assertEquals("company", http.details["field"])
    }

    @Test
    fun `maps timeout and unknown host`() {
        val mapper = DefaultErrorMapper(json, onlineObserver)
        assertTrue(mapper.toNetworkError(SocketTimeoutException("t")) is NetworkError.Timeout)
        val unknownHost = mapper.toNetworkError(UnknownHostException("h")) as NetworkError.Unknown
        assertTrue(unknownHost.message.contains("address"))
        val refused = mapper.toNetworkError(ConnectException("refused")) as NetworkError.Unknown
        assertTrue(refused.message.contains("not reachable"))
    }

    @Test
    fun `maps offline IOException to NoConnectivity`() {
        val mapper = DefaultErrorMapper(json, offlineObserver)
        val error = mapper.toNetworkError(java.io.IOException("broken pipe"))
        assertTrue(error is NetworkError.NoConnectivity)
    }

    @Test
    fun `maps NetworkError to AppError`() {
        val mapper = DefaultErrorMapper(json, onlineObserver)
        assertTrue(mapper.toAppError(NetworkError.NoConnectivity) is AppError.Offline)
        assertTrue(mapper.toAppError(NetworkError.Timeout()) is AppError.Timeout)
        assertTrue(mapper.toAppError(NetworkError.Serialization()) is AppError.Serialization)
        val remote = mapper.toAppError(
            NetworkError.Http(503, "SERVICE_UNAVAILABLE", "down"),
        ) as AppError.Remote
        assertEquals(503, remote.httpStatus)
        assertEquals("SERVICE_UNAVAILABLE", remote.code)
        val unknown = mapper.toAppError(NetworkError.Unknown("Connector is not reachable.", cause = null))
        assertTrue(unknown is AppError.Message)
    }

    @Test
    fun `online IOException does not forward raw transport text`() {
        val mapper = DefaultErrorMapper(json, onlineObserver)
        val network = mapper.toNetworkError(java.io.IOException("failed to connect to /192.168.1.9 (port 9000)"))
        val unknown = network as NetworkError.Unknown
        assertFalse(unknown.message.contains("192.168"))
        assertFalse(unknown.message.contains("port 9000"))
        val visible = com.jajusri.venture.core.common.UserVisibleErrorText.fromAppError(mapper.toAppError(network))
        assertFalse(visible.contains("192.168"))
        assertFalse(visible.contains("SQLite"))
    }

    @Test
    fun `ApiException unwraps NetworkError`() {
        val mapper = DefaultErrorMapper(json, onlineObserver)
        val original = NetworkError.Http(404, "NOT_FOUND", "missing")
        assertEquals(original, mapper.toNetworkError(ApiException(original)))
    }

    private fun httpException(code: Int, body: String): HttpException {
        val responseBody = body.toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Unit>(code, responseBody))
    }
}

class RetryPolicyTest {

    @Test
    fun `Default policy retries retryable failures then succeeds`() = runBlocking {
        var attempts = 0
        val result = withRetry(
            policy = RetryPolicy(
                maxAttempts = 3,
                initialDelayMillis = 1,
                maxDelayMillis = 2,
                jitterRatio = 0.0,
            ),
            random = Random(0),
        ) {
            attempts += 1
            if (attempts < 3) {
                ApiResult.Failure(NetworkError.Timeout())
            } else {
                ApiResult.Success("ok")
            }
        }
        assertEquals(ApiResult.Success("ok"), result)
        assertEquals(3, attempts)
    }

    @Test
    fun `does not retry non-retryable errors`() = runBlocking {
        var attempts = 0
        val result = withRetry(RetryPolicy(maxAttempts = 3, initialDelayMillis = 1, jitterRatio = 0.0)) {
            attempts += 1
            ApiResult.Failure(NetworkError.Http(400, "VALIDATION_ERROR", "bad"))
        }
        assertTrue(result is ApiResult.Failure)
        assertEquals(1, attempts)
    }

    @Test
    fun `isRetryable classification`() {
        assertTrue(NetworkError.Timeout().isRetryable())
        assertTrue(NetworkError.Http(503, null, "x").isRetryable())
        assertFalse(NetworkError.Http(400, null, "x").isRetryable())
        assertFalse(NetworkError.Serialization().isRetryable())
    }

    @Test
    fun `None policy never delays`() {
        assertEquals(null, RetryPolicy.None.delayBeforeRetryMillis(0, Random(0)))
    }
}

class ApiResultTest {

    @Test
    fun `map and flatMap preserve failures`() {
        val failure: ApiResult<Int> = ApiResult.Failure(NetworkError.NoConnectivity)
        assertTrue(failure.map { it + 1 } is ApiResult.Failure)
        assertTrue(failure.flatMap { ApiResult.Success(it) } is ApiResult.Failure)
        assertEquals(2, ApiResult.Success(1).map { it + 1 }.getOrNull())
    }
}

private class FakeConnectivityObserver(
    private val online: Boolean,
) : NetworkConnectivityObserver {
    override val isOnline: Flow<Boolean> = flowOf(online)
    override fun current(): Boolean = online
}
