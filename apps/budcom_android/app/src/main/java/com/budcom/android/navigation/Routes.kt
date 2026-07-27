package com.budcom.android.navigation

import android.net.Uri

/**
 * Type-safe navigation route identifiers.
 */
object Routes {
    const val HOME = "home"
    const val SERVER_CONFIG = "server_config"
    const val COMPANY = "company"
    const val MASTER_DATA = "master_data"
    const val SEARCH = "search"
    const val SYNC = "sync"
    const val DIAGNOSTICS = "diagnostics"
    const val LEDGERS = "ledgers?q={q}"
    const val STOCK_ITEMS = "stock_items?q={q}"
    const val VOUCHERS = "vouchers?q={q}"
    const val VOUCHER_DETAILS = "vouchers/detail/{voucherId}"

    const val QUERY_ARG = "q"

    fun ledgers(query: String = ""): String =
        "ledgers?q=${Uri.encode(query)}"

    fun stockItems(query: String = ""): String =
        "stock_items?q=${Uri.encode(query)}"

    fun vouchers(query: String = ""): String =
        "vouchers?q=${Uri.encode(query)}"

    fun voucherDetails(voucherId: String): String =
        "vouchers/detail/${Uri.encode(voucherId)}"
}
