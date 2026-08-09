package com.budcom.android.core.connectorauth.domain.model

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class ConnectorHttpMethod { GET, POST, DELETE }

/** Mirrors the legacy `@SyncHttp` long-timeout profile (see `core/network/NetworkConstants.kt`). */
enum class ConnectorTimeoutProfile { STANDARD, SYNC }

private fun jsonBodyOf(vararg fields: Pair<String, String>): String =
    buildJsonObject { fields.forEach { (key, value) -> put(key, value) } }.toString()

/**
 * One typed member per Connector route used by the secure transport. Business operations are
 * classified DEVICE_CREDENTIAL_REQUIRED by the Phase 3P
 * route matrix (see `BUDCOM_PHASE3P_AUTHENTICATED_ROUTE_CALLER_MATRIX_20260805_200456.csv`),
 * reconciled manually against committed Connector route source rather than parsed from the CSV at
 * build time. A caller may only ever construct one of these fixed members — there is no
 * `executeUrl(String)` escape hatch, so an arbitrary raw URL can never be dispatched through
 * [com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort].
 *
 * `PublicHealth` and `PublicReadiness` are the only PUBLIC_HEALTH_MINIMAL exceptions: they use
 * the same pinned client and trusted endpoint but explicitly omit the bearer credential.
 * Deliberately excludes the pairing-bootstrap routes and `/device/pair`/`/device/validate-token`
 * (PUBLIC_BOOTSTRAP / LEGACY_COMPATIBILITY_ONLY
 * — pre-credential by definition); the Desktop-control-only routes (`/device/list`,
 * `DELETE /device/:id`, the two pairing-credential admin routes) — those remain
 * requireDesktopControlToken-gated, never reachable with a device credential.
 */
sealed class AuthenticatedConnectorOperation(
    val method: ConnectorHttpMethod,
    val timeoutProfile: ConnectorTimeoutProfile = ConnectorTimeoutProfile.STANDARD,
    /** Public health routes remain pinned to the trusted certificate but receive no bearer. */
    val requiresCredential: Boolean = true,
) {
    abstract val pathSegments: List<String>
    open val queryParams: Map<String, String> = emptyMap()
    open val jsonBody: String? = null

    // ---- Diagnostics ----

    data object PublicHealth : AuthenticatedConnectorOperation(
        ConnectorHttpMethod.GET,
        requiresCredential = false,
    ) {
        override val pathSegments = listOf("health")
    }

    data object PublicReadiness : AuthenticatedConnectorOperation(
        ConnectorHttpMethod.GET,
        requiresCredential = false,
    ) {
        override val pathSegments = listOf("ready")
    }

    data object DiagnosticsConnection : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("diagnostics", "connection")
    }

    data object DiagnosticsExtraction : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("diagnostics", "extraction")
    }

    // ---- Companies and session ----

    data object GetCompanies : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies")
    }

    data object GetSession : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("session")
    }

    data class SelectCompany(val companyId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.POST) {
        override val pathSegments = listOf("session", "company")
        override val jsonBody = jsonBodyOf("companyId" to companyId)
    }

    data object ClearSessionCompany : AuthenticatedConnectorOperation(ConnectorHttpMethod.DELETE) {
        override val pathSegments = listOf("session", "company")
    }

    data object ValidateSession : AuthenticatedConnectorOperation(ConnectorHttpMethod.POST) {
        override val pathSegments = listOf("session", "validate")
    }

    // ---- Company master data ----

    data class GetCompany(val companyId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies", companyId)
    }

    data class GetLedgerGroups(val companyId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies", companyId, "ledger-groups")
    }

    data class GetCompanyLedgers(val companyId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies", companyId, "ledgers")
    }

    data class GetStockGroups(val companyId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies", companyId, "stock-groups")
    }

    data class GetStockCategories(val companyId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies", companyId, "stock-categories")
    }

    data class GetCompanyStockItems(val companyId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies", companyId, "stock-items")
    }

    data class GetUnits(val companyId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies", companyId, "units")
    }

    data class GetGodowns(val companyId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies", companyId, "godowns")
    }

    data class GetCostCategories(val companyId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies", companyId, "cost-categories")
    }

    data class GetCostCentres(val companyId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies", companyId, "cost-centres")
    }

    data class GetVoucherTypes(val companyId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies", companyId, "voucher-types")
    }

    data class GetGstRegistrations(val companyId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies", companyId, "gst-registrations")
    }

    // ---- Ledgers ----

    data class GetLedgers(override val queryParams: Map<String, String> = emptyMap()) :
        AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("ledgers")
    }

    data class GetLedgerById(val ledgerId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("ledgers", ledgerId)
    }

    data object StartLedgerSync : AuthenticatedConnectorOperation(ConnectorHttpMethod.POST, ConnectorTimeoutProfile.SYNC) {
        override val pathSegments = listOf("sync", "ledgers")
    }

    data object CancelLedgerSync : AuthenticatedConnectorOperation(ConnectorHttpMethod.POST) {
        override val pathSegments = listOf("sync", "ledgers", "cancel")
    }

    data object LedgerSyncStatus : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("sync", "ledgers", "status")
    }

    data object LedgerSyncStatistics : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("sync", "ledgers", "statistics")
    }

    data class LedgerSyncRuns(override val queryParams: Map<String, String> = emptyMap()) :
        AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("sync", "ledgers", "runs")
    }

    data class LedgerSyncRunById(val runId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("sync", "ledgers", "runs", runId)
    }

    data object ClearLedgerSyncCache : AuthenticatedConnectorOperation(ConnectorHttpMethod.POST) {
        override val pathSegments = listOf("sync", "ledgers", "clear-cache")
    }

    data object LedgerStorageIntegrityCheck : AuthenticatedConnectorOperation(ConnectorHttpMethod.POST, ConnectorTimeoutProfile.SYNC) {
        override val pathSegments = listOf("storage", "ledgers", "integrity-check")
    }

    data object LedgerStorageBackup : AuthenticatedConnectorOperation(ConnectorHttpMethod.POST, ConnectorTimeoutProfile.SYNC) {
        override val pathSegments = listOf("storage", "ledgers", "backup")
    }

    // ---- Stock items ----

    data class GetStockItems(override val queryParams: Map<String, String> = emptyMap()) :
        AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("stock-items")
    }

    data class GetStockItemById(val stockItemId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("stock-items", stockItemId)
    }

    data object StartStockItemSync : AuthenticatedConnectorOperation(ConnectorHttpMethod.POST, ConnectorTimeoutProfile.SYNC) {
        override val pathSegments = listOf("sync", "stock-items")
    }

    data object CancelStockItemSync : AuthenticatedConnectorOperation(ConnectorHttpMethod.POST) {
        override val pathSegments = listOf("sync", "stock-items", "cancel")
    }

    data object StockItemSyncStatus : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("sync", "stock-items", "status")
    }

    data object StockItemSyncStatistics : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("sync", "stock-items", "statistics")
    }

    data class StockItemSyncRuns(override val queryParams: Map<String, String> = emptyMap()) :
        AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("sync", "stock-items", "runs")
    }

    data class StockItemSyncRunById(val runId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("sync", "stock-items", "runs", runId)
    }

    data object ClearStockItemSyncCache : AuthenticatedConnectorOperation(ConnectorHttpMethod.POST) {
        override val pathSegments = listOf("sync", "stock-items", "clear-cache")
    }

    data object StockItemStorageIntegrityCheck : AuthenticatedConnectorOperation(ConnectorHttpMethod.POST, ConnectorTimeoutProfile.SYNC) {
        override val pathSegments = listOf("storage", "stock-items", "integrity-check")
    }

    data object StockItemStorageBackup : AuthenticatedConnectorOperation(ConnectorHttpMethod.POST, ConnectorTimeoutProfile.SYNC) {
        override val pathSegments = listOf("storage", "stock-items", "backup")
    }

    // ---- Vouchers ----

    data object StartVoucherSync : AuthenticatedConnectorOperation(ConnectorHttpMethod.POST, ConnectorTimeoutProfile.SYNC) {
        override val pathSegments = listOf("sync", "vouchers")
    }

    data class ListVouchers(override val queryParams: Map<String, String> = emptyMap()) :
        AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("api", "v1", "vouchers")
    }

    data class SearchVouchers(override val queryParams: Map<String, String> = emptyMap()) :
        AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("api", "v1", "vouchers", "search")
    }

    data class ListVoucherSnapshots(override val queryParams: Map<String, String> = emptyMap()) :
        AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("api", "v1", "vouchers", "snapshots")
    }

    data class GetVoucherSnapshot(val snapshotId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("api", "v1", "vouchers", "snapshots", snapshotId)
    }

    /**
     * `company` is a mandatory query parameter on the Connector route (validated server-side,
     * never inferred from session — confirmed by direct read of
     * `connector/budcom_connector/src/api/routes/vouchers.ts`'s `requireCompany` call). Unlike
     * every other member above, this operation validates its own arguments at construction time:
     * neither field may be blank, since a blank value would silently produce a malformed request
     * this route's own server-side validation would reject anyway — failing fast here surfaces
     * that at the call site instead.
     */
    data class GetVoucherById(val voucherId: String, val companyId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        init {
            require(voucherId.isNotBlank()) { "voucherId must not be blank." }
            require(companyId.isNotBlank()) { "companyId must not be blank." }
        }
        override val pathSegments = listOf("api", "v1", "vouchers", voucherId)
        override val queryParams = mapOf("company" to companyId)
    }

    // ---- Reserved protected stubs (inert 501s today; classified now so they inherit the gate) ----

    data class ReservedCompanyLedgerById(val companyId: String, val ledgerId: String) :
        AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies", companyId, "ledgers", ledgerId)
    }

    data class ReservedLedgerTransactions(val companyId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies", companyId, "ledger-transactions")
    }

    data class ReservedCompanyVouchers(val companyId: String) : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies", companyId, "vouchers")
    }

    data class ReservedCompanyVoucherById(val companyId: String, val voucherId: String) :
        AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("companies", companyId, "vouchers", voucherId)
    }

    data object ReservedSyncCheckpoint : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("sync", "checkpoint")
    }

    // ---- Device self-service (confirmed part of the protected contract by Phase 3Q, item 6) ----

    data object GetTrustedCompanies : AuthenticatedConnectorOperation(ConnectorHttpMethod.GET) {
        override val pathSegments = listOf("device", "trusted-companies")
    }
}
