package com.budcom.android.feature.masterdata.presentation

import com.budcom.android.core.common.AppError

/**
 * Shared UI error model for Master Data list browsers (Ledgers, Stock Items, …).
 */
sealed interface MasterDataUiError {
    data class Offline(val message: String) : MasterDataUiError
    data class Timeout(val message: String) : MasterDataUiError
    data class Remote(val message: String, val httpStatus: Int?) : MasterDataUiError
    data class Message(val message: String) : MasterDataUiError
    data class Unexpected(val message: String) : MasterDataUiError
}

fun AppError.toMasterDataUiError(): MasterDataUiError = when (this) {
    is AppError.Offline -> MasterDataUiError.Offline("Device is offline.")
    is AppError.Timeout -> MasterDataUiError.Timeout("The request timed out.")
    is AppError.Remote -> MasterDataUiError.Remote(message, httpStatus)
    is AppError.Serialization -> MasterDataUiError.Message(message)
    is AppError.Message -> MasterDataUiError.Message(message)
    is AppError.Unexpected -> MasterDataUiError.Unexpected(
        cause.message ?: "An unexpected error occurred.",
    )
}

fun MasterDataUiError.displayMessage(): String = when (this) {
    is MasterDataUiError.Offline -> message
    is MasterDataUiError.Timeout -> message
    is MasterDataUiError.Remote -> message
    is MasterDataUiError.Message -> message
    is MasterDataUiError.Unexpected -> message
}
