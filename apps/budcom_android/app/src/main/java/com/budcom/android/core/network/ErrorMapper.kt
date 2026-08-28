package com.budcom.android.core.network

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.UserVisibleErrorText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps throwables and Connector HTTP failures into [NetworkError], and network
 * errors into domain [AppError] for repository boundaries.
 */
interface ErrorMapper {
    fun toNetworkError(throwable: Throwable): NetworkError
    fun toAppError(error: NetworkError): AppError
    fun toAppError(throwable: Throwable): AppError = toAppError(toNetworkError(throwable))
}

/**
 * Default mapper that understands Connector `{ code, message, details? }` bodies
 * and common OkHttp / Retrofit transport failures.
 */
@Singleton
class DefaultErrorMapper @Inject constructor(
    private val json: Json,
    private val connectivityObserver: NetworkConnectivityObserver,
) : ErrorMapper {

    override fun toNetworkError(throwable: Throwable): NetworkError {
        if (throwable is CancellationException) throw throwable

        if (throwable is ApiException) {
            return throwable.error
        }

        if (throwable is HttpException) {
            return mapHttpException(throwable)
        }

        if (throwable is SocketTimeoutException ||
            throwable.cause is SocketTimeoutException
        ) {
            return NetworkError.Timeout(cause = throwable)
        }

        if (throwable is UnknownHostException ||
            throwable.cause is UnknownHostException
        ) {
            return NetworkError.Unknown(
                message = "Connector address could not be resolved. Check the desktop IP address and port.",
                cause = throwable,
            )
        }

        if (throwable is ConnectException ||
            throwable.cause is ConnectException ||
            throwable is NoRouteToHostException ||
            throwable.cause is NoRouteToHostException
        ) {
            return NetworkError.Unknown(
                message = "Connector is not reachable. Check that it is running, LAN-enabled, and allowed through Windows Firewall.",
                cause = throwable,
            )
        }

        if (throwable is kotlinx.serialization.SerializationException ||
            throwable.cause is kotlinx.serialization.SerializationException
        ) {
            return NetworkError.Serialization(cause = throwable)
        }

        if (throwable is IOException) {
            if (!connectivityObserver.current()) {
                return NetworkError.NoConnectivity
            }
            return NetworkError.Unknown(
                message = UserVisibleErrorText.NETWORK,
                cause = throwable,
            )
        }

        return NetworkError.Unknown(
            message = UserVisibleErrorText.NETWORK,
            cause = throwable,
        )
    }

    override fun toAppError(error: NetworkError): AppError = when (error) {
        is NetworkError.NoConnectivity -> AppError.Offline()
        is NetworkError.Timeout -> AppError.Timeout(cause = error.cause)
        is NetworkError.Http -> AppError.Remote(
            httpStatus = error.httpStatus,
            code = error.code,
            message = error.message,
        )
        is NetworkError.Serialization -> AppError.Serialization(
            message = error.message,
            cause = error.cause,
        )
        is NetworkError.Unknown -> AppError.Message(
            message = UserVisibleErrorText.sanitizeOr(error.message, UserVisibleErrorText.NETWORK),
            cause = error.cause,
        )
    }

    private fun mapHttpException(exception: HttpException): NetworkError {
        val httpStatus = exception.code()
        val rawBody = runCatching { exception.response()?.errorBody()?.string() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

        val parsed = rawBody?.let { parseConnectorErrorBody(it) }
        return NetworkError.Http(
            httpStatus = httpStatus,
            code = parsed?.code,
            message = parsed?.message
                ?: UserVisibleErrorText.fromRemote(httpStatus, exception.message().orEmpty()),
            details = parsed?.details.orEmpty(),
        )
    }

    private fun parseConnectorErrorBody(body: String): ParsedConnectorError? {
        return runCatching {
            val element = json.parseToJsonElement(body)
            if (element !is JsonObject) return null
            val code = element["code"]?.jsonPrimitive?.contentOrNull
            val message = element["message"]?.jsonPrimitive?.contentOrNull
            if (code == null && message == null) return null
            val details = element["details"]?.jsonObject
                ?.mapNotNull { (key, value) ->
                    val primitive = value as? JsonPrimitive ?: return@mapNotNull null
                    key to (primitive.contentOrNull ?: return@mapNotNull null)
                }
                ?.toMap()
                .orEmpty()
            ParsedConnectorError(
                code = code,
                message = message,
                details = details,
            )
        }.getOrNull()
    }

    private data class ParsedConnectorError(
        val code: String?,
        val message: String?,
        val details: Map<String, String>,
    )
}
