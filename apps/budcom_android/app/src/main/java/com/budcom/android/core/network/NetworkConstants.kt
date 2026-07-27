package com.budcom.android.core.network

/**
 * Shared HTTP client settings for the BudCom Connector.
 *
 * Endpoint paths are not defined here — Retrofit service interfaces are added only
 * after Connector routes are supplied.
 */
object NetworkConstants {
    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L
    const val CALL_TIMEOUT_SECONDS = 60L
}
