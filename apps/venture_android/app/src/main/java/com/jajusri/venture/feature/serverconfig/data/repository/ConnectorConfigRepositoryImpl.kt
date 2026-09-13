package com.jajusri.venture.feature.serverconfig.data.repository

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.network.ApiResult
import com.jajusri.venture.core.network.ConnectorBaseUrlProvider
import com.jajusri.venture.core.network.ErrorMapper
import com.jajusri.venture.core.util.DispatcherProvider
import com.jajusri.venture.core.util.TimeProvider
import com.jajusri.venture.feature.serverconfig.data.local.ConnectorBaseUrlLocalStore
import com.jajusri.venture.feature.serverconfig.data.remote.ConnectorHealthRemoteDataSource
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.jajusri.venture.feature.serverconfig.domain.repository.ConnectorConfigRepository
import com.jajusri.venture.feature.serverconfig.domain.validation.ConnectorUrlValidator
import com.jajusri.venture.feature.serverconfig.domain.validation.toUserMessage
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
