package com.budcom.android.feature.serverconfig.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.UserVisibleErrorText
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.DefaultConnectorBaseUrlProvider
import com.budcom.android.feature.serverconfig.domain.repository.ConnectorConfigRepository
import com.budcom.android.feature.serverconfig.domain.validation.ConnectorUrlValidator
import com.budcom.android.feature.serverconfig.domain.validation.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Coordinates server configuration and Connector health probe UI state.
 */
@HiltViewModel
class ServerConfigViewModel @Inject constructor(
    private val repository: ConnectorConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ServerConfigUiState(
            urlInput = DefaultConnectorBaseUrlProvider.DEFAULT,
            savedUrl = DefaultConnectorBaseUrlProvider.DEFAULT,
        ),
    )
    val uiState: StateFlow<ServerConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeBaseUrl().collect { saved ->
                _uiState.update { state ->
                    val shouldSyncInput = !state.isBusy &&
                        (state.urlInput == state.savedUrl || state.urlInput.isBlank())
                    state.copy(
                        savedUrl = saved,
                        urlInput = if (shouldSyncInput) saved else state.urlInput,
                    )
                }
            }
        }
    }

    fun onEvent(event: ServerConfigEvent) {
        when (event) {
            is ServerConfigEvent.UrlChanged -> onUrlChanged(event.value)
            ServerConfigEvent.SaveClicked -> saveUrl()
            ServerConfigEvent.TestConnectionClicked -> testConnection()
            ServerConfigEvent.RetryClicked -> testConnection()
            ServerConfigEvent.SaveFeedbackConsumed -> {
                _uiState.update { it.copy(saveFeedback = null) }
            }
        }
    }

    private fun onUrlChanged(value: String) {
        val validation = ConnectorUrlValidator.validate(value)
        _uiState.update {
            it.copy(
                urlInput = value,
                urlValidationError = when (validation) {
                    is ConnectorUrlValidator.Result.Valid -> null
                    is ConnectorUrlValidator.Result.Invalid -> validation.reason.toUserMessage()
                },
                saveFeedback = null,
            )
        }
    }

    private fun saveUrl() {
        val state = _uiState.value
        if (state.isBusy) return
        val validation = ConnectorUrlValidator.validate(state.urlInput)
        if (validation is ConnectorUrlValidator.Result.Invalid) {
            _uiState.update {
                it.copy(urlValidationError = validation.reason.toUserMessage())
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveFeedback = null) }
            when (val result = repository.saveBaseUrl(state.urlInput)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            savedUrl = result.value,
                            urlInput = result.value,
                            urlValidationError = null,
                            saveFeedback = "Connector URL saved.",
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            urlValidationError = result.error.toMessage(),
                            saveFeedback = null,
                        )
                    }
                }
            }
        }
    }

    private fun testConnection() {
        val state = _uiState.value
        if (state.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, connection = ConnectionUiState.Loading) }
            when (val result = repository.testConnection()) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            connection = ConnectionUiState.Success(result.value),
                        )
                    }
                }
                is AppResult.Failure -> {
                    val mapped = result.error.toConnectionError()
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            connection = ConnectionUiState.Error(
                                kind = mapped.first,
                                message = mapped.second,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun AppError.toMessage(): String = UserVisibleErrorText.fromAppError(this)

private fun AppError.toConnectionError(): Pair<ConnectionErrorKind, String> = when (this) {
    is AppError.Offline -> ConnectionErrorKind.Offline to toMessage()
    is AppError.Timeout -> ConnectionErrorKind.Timeout to toMessage()
    is AppError.Remote -> ConnectionErrorKind.Http to toMessage()
    is AppError.Serialization -> ConnectionErrorKind.Serialization to toMessage()
    is AppError.Message -> ConnectionErrorKind.Unknown to toMessage()
    is AppError.Unexpected -> ConnectionErrorKind.Unknown to toMessage()
}
