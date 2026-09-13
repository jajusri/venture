package com.jajusri.venture.core.network

/**
 * Shared HTTP client settings for the Venture Connector.
 *
 * Endpoint paths are not defined here — Retrofit service interfaces are added only
 * after Connector routes are supplied.
 */
object NetworkConstants {
    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L
    const val CALL_TIMEOUT_SECONDS = 60L

    /** Blocking sync POSTs may run for several minutes against Tally. */
    const val SYNC_CONNECT_TIMEOUT_SECONDS = 15L
    const val SYNC_READ_TIMEOUT_SECONDS = 600L
    const val SYNC_WRITE_TIMEOUT_SECONDS = 60L
    /** 0 disables OkHttp call timeout so read timeout governs long syncs. */
    const val SYNC_CALL_TIMEOUT_SECONDS = 0L
}
