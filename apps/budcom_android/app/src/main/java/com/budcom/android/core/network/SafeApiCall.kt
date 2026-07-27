package com.budcom.android.core.network

/**
 * Executes a suspending Connector call and maps any throwable into [ApiResult.Failure].
 *
 * Use from remote data sources. Apply [withRetry] for idempotent GET endpoints.
 */
suspend fun <T> safeApiCall(
    errorMapper: ErrorMapper,
    connectivityObserver: NetworkConnectivityObserver? = null,
    block: suspend () -> T,
): ApiResult<T> {
    if (connectivityObserver != null && !connectivityObserver.current()) {
        return ApiResult.Failure(NetworkError.NoConnectivity)
    }
    return try {
        ApiResult.Success(block())
    } catch (throwable: Throwable) {
        ApiResult.Failure(errorMapper.toNetworkError(throwable))
    }
}
