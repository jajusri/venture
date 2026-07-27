package com.budcom.android.feature.serverconfig.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ConnectorBaseUrlProvider
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.serverconfig.data.local.ConnectorBaseUrlLocalStore
import com.budcom.android.feature.serverconfig.data.remote.ConnectorHealthRemoteDataSource
import com.budcom.android.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.budcom.android.feature.serverconfig.domain.repository.ConnectorConfigRepository
import com.budcom.android.feature.serverconfig.domain.validation.ConnectorUrlValidator
import com.budcom.android.feature.serverconfig.domain.validation.toUserMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects DataStore for base URL persistence and remote health/readiness probes.
 */
@Singleton
class ConnectorConfigRepositoryImpl @Inject constructor(
    private val localDataSource: ConnectorBaseUrlLocalStore,
    private val remoteDataSource: ConnectorHealthRemoteDataSource,
    private val baseUrlProvider: ConnectorBaseUrlProvider,
    private val errorMapper: ErrorMapper,
    private val dispatchers: DispatcherProvider,
    private val timeProvider: TimeProvider,
) : ConnectorConfigRepository {

    override fun observeBaseUrl(): Flow<String> = localDataSource.baseUrl

    override fun currentBaseUrl(): String = baseUrlProvider.snapshot()

    override suspend fun saveBaseUrl(rawUrl: String): AppResult<String> = withContext(dispatchers.io) {
        when (val validation = ConnectorUrlValidator.validate(rawUrl)) {
            is ConnectorUrlValidator.Result.Invalid -> {
                AppResult.Failure(
                    AppError.Message(message = validation.reason.toUserMessage()),
                )
            }
            is ConnectorUrlValidator.Result.Valid -> {
                localDataSource.save(validation.normalized)
                baseUrlProvider.updateInMemory(validation.normalized)
                AppResult.Success(validation.normalized)
            }
        }
    }

    override suspend fun probeConnection(): AppResult<ConnectorConnectionProbe> =
        withContext(dispatchers.io) {
            when (val healthResult = remoteDataSource.fetchHealth()) {
                is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(healthResult.error))
                is ApiResult.Success -> {
                    val readiness = when (val readyResult = remoteDataSource.fetchReadiness()) {
                        is ApiResult.Success -> readyResult.data
                        is ApiResult.Failure -> null
                    }
                    AppResult.Success(
                        ConnectorConnectionProbe(
                            health = healthResult.data,
                            readiness = readiness,
                            checkedAtEpochMillis = timeProvider.nowEpochMillis(),
                        ),
                    )
                }
            }
        }
}
