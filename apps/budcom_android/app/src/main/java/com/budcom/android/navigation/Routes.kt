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
    const val LEDGERS = "ledgers"
    const val STOCK_ITEMS = "stock_items"
    const val VOUCHERS = "vouchers"
    const val VOUCHER_DETAILS = "vouchers/detail/{voucherId}"

    fun voucherDetails(voucherId: String): String =
        "vouchers/detail/${Uri.encode(voucherId)}"
}
