package com.budcom.android.navigation

import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Type-safe navigation route identifiers.
 */
object Routes {
    const val HOME = "home"
    const val CONNECTOR_DISCOVERY = "connector_discovery"
    const val SECURE_PAIRING = "secure_pairing"
    const val SERVER_CONFIG = "server_config"
    const val COMPANY = "company"
    const val MASTER_DATA = "master_data"
    const val SEARCH = "search"
    const val SYNC = "sync"
    const val DIAGNOSTICS = "diagnostics"
    const val SETTINGS = "settings"
    const val TRUST_STATUS = "trust_status"
    const val LEDGERS = "ledgers?q={q}"
    const val STOCK_ITEMS = "stock_items?q={q}"
    const val VOUCHERS = "vouchers?q={q}"
    const val VOUCHER_DETAILS = "vouchers/detail/{voucherId}"
    const val LEDGER_STATEMENT = "ledgers/statement/{ledgerId}"
    const val CONNECT = "connect?q={q}"
    const val DINCHARYA = "dincharya"
    const val BUSINESS_PROFILE = "business_profile"
    const val CATALOGUE = "catalogue"
    const val CATALOGUE_DETAIL = "catalogue/{productId}"
    const val CATALOGUE_STOCK_ITEM_PICKER = "catalogue/link-stock-item"
    const val TRANSACTION_COMPOSER = "transaction/compose"
    const val STRUCTURED_INBOX = "transaction/inbox"
    const val RECEIVED_ORDER = "transaction/received/{envelopeId}/{senderBusinessId}/{orderId}/{orderVersion}"
    const val RECEIVED_REVISION = "transaction/revision/{envelopeId}/{senderBusinessId}/{orderId}/{orderVersion}"

    fun receivedOrder(envelopeId: String, senderBusinessId: String, orderId: String, orderVersion: Int): String =
        "transaction/received/${encodeRouteSegment(envelopeId)}/${encodeRouteSegment(senderBusinessId)}/${encodeRouteSegment(orderId)}/$orderVersion"

    const val SELLER_REVISION = "transaction/seller-revision/{envelopeId}/{buyerBusinessId}/{orderId}/{orderVersion}"

    fun receivedRevision(envelopeId: String, senderBusinessId: String, orderId: String, orderVersion: Int): String =
        "transaction/revision/${encodeRouteSegment(envelopeId)}/${encodeRouteSegment(senderBusinessId)}/${encodeRouteSegment(orderId)}/$orderVersion"

    fun sellerRevision(envelopeId: String, buyerBusinessId: String, orderId: String, orderVersion: Int): String =
        "transaction/seller-revision/${encodeRouteSegment(envelopeId)}/${encodeRouteSegment(buyerBusinessId)}/${encodeRouteSegment(orderId)}/$orderVersion"
    const val PARTY_DETAIL = "connect/party/{partyId}"
    const val PROSPECT_CREATE = "connect/prospect/new"
    const val PARTY_XML_EXPORT = "connect/party/{partyId}/xml-export"

    const val QUERY_ARG = "q"

    fun connect(query: String = ""): String =
        "connect?q=${Uri.encode(query)}"

    fun partyDetail(partyId: String): String =
        "connect/party/${Uri.encode(partyId)}"

    fun partyXmlExport(partyId: String): String =
        "connect/party/${Uri.encode(partyId)}/xml-export"

    fun ledgers(query: String = ""): String =
        "ledgers?q=${Uri.encode(query)}"

    fun ledgerStatement(ledgerId: String): String =
        "ledgers/statement/${Uri.encode(ledgerId)}"

    fun stockItems(query: String = ""): String =
        "stock_items?q=${Uri.encode(query)}"

    fun vouchers(query: String = ""): String =
        "vouchers?q=${Uri.encode(query)}"

    fun voucherDetails(voucherId: String): String =
        "vouchers/detail/${Uri.encode(voucherId)}"

    fun catalogueDetail(productId: String): String =
        "catalogue/${Uri.encode(productId)}"

    private fun encodeRouteSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
