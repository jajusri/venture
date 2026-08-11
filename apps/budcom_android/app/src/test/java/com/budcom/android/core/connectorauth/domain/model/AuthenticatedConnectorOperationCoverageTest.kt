package com.budcom.android.core.connectorauth.domain.model

import com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private fun AuthenticatedConnectorOperation.route(): String = "/" + pathSegments.joinToString("/")

/** One instance of every member of the sealed [AuthenticatedConnectorOperation] hierarchy. */
private fun allOperations(): List<AuthenticatedConnectorOperation> = listOf(
    AuthenticatedConnectorOperation.DiagnosticsConnection,
    AuthenticatedConnectorOperation.DiagnosticsExtraction,
    AuthenticatedConnectorOperation.GetCompanies,
    AuthenticatedConnectorOperation.GetSession,
    AuthenticatedConnectorOperation.SelectCompany("company-1"),
    AuthenticatedConnectorOperation.ClearSessionCompany,
    AuthenticatedConnectorOperation.ValidateSession,
    AuthenticatedConnectorOperation.GetCompany("company-1"),
    AuthenticatedConnectorOperation.GetLedgerGroups("company-1"),
    AuthenticatedConnectorOperation.GetCompanyLedgers("company-1"),
    AuthenticatedConnectorOperation.GetStockGroups("company-1"),
    AuthenticatedConnectorOperation.GetStockCategories("company-1"),
    AuthenticatedConnectorOperation.GetCompanyStockItems("company-1"),
    AuthenticatedConnectorOperation.GetUnits("company-1"),
    AuthenticatedConnectorOperation.GetGodowns("company-1"),
    AuthenticatedConnectorOperation.GetCostCategories("company-1"),
    AuthenticatedConnectorOperation.GetCostCentres("company-1"),
    AuthenticatedConnectorOperation.GetVoucherTypes("company-1"),
    AuthenticatedConnectorOperation.GetGstRegistrations("company-1"),
    AuthenticatedConnectorOperation.GetLedgers(),
    AuthenticatedConnectorOperation.GetLedgerById("ledger-1"),
    AuthenticatedConnectorOperation.GetLedgerStatement("ledger-1"),
    AuthenticatedConnectorOperation.StartLedgerSync,
    AuthenticatedConnectorOperation.CancelLedgerSync,
    AuthenticatedConnectorOperation.LedgerSyncStatus,
    AuthenticatedConnectorOperation.LedgerSyncStatistics,
    AuthenticatedConnectorOperation.LedgerSyncRuns(),
    AuthenticatedConnectorOperation.LedgerSyncRunById("run-1"),
    AuthenticatedConnectorOperation.ClearLedgerSyncCache,
    AuthenticatedConnectorOperation.LedgerStorageIntegrityCheck,
    AuthenticatedConnectorOperation.LedgerStorageBackup,
    AuthenticatedConnectorOperation.GetStockItems(),
    AuthenticatedConnectorOperation.GetStockItemById("stock-1"),
    AuthenticatedConnectorOperation.StartStockItemSync,
    AuthenticatedConnectorOperation.CancelStockItemSync,
    AuthenticatedConnectorOperation.StockItemSyncStatus,
    AuthenticatedConnectorOperation.StockItemSyncStatistics,
    AuthenticatedConnectorOperation.StockItemSyncRuns(),
    AuthenticatedConnectorOperation.StockItemSyncRunById("run-1"),
    AuthenticatedConnectorOperation.ClearStockItemSyncCache,
    AuthenticatedConnectorOperation.StockItemStorageIntegrityCheck,
    AuthenticatedConnectorOperation.StockItemStorageBackup,
    AuthenticatedConnectorOperation.StartVoucherSync,
    AuthenticatedConnectorOperation.ListVouchers(),
    AuthenticatedConnectorOperation.SearchVouchers(),
    AuthenticatedConnectorOperation.ListVoucherSnapshots(),
    AuthenticatedConnectorOperation.GetVoucherSnapshot("snapshot-1"),
    AuthenticatedConnectorOperation.GetVoucherById("voucher-1", "company-1"),
    AuthenticatedConnectorOperation.ReservedCompanyLedgerById("company-1", "ledger-1"),
    AuthenticatedConnectorOperation.ReservedLedgerTransactions("company-1"),
    AuthenticatedConnectorOperation.ReservedCompanyVouchers("company-1"),
    AuthenticatedConnectorOperation.ReservedCompanyVoucherById("company-1", "voucher-1"),
    AuthenticatedConnectorOperation.ReservedSyncCheckpoint,
    AuthenticatedConnectorOperation.GetTrustedCompanies,
)

class AuthenticatedConnectorOperationCoverageTest {

    // 51. every Phase 3P DEVICE_CREDENTIAL_REQUIRED business route (per the Phase 4 spec's own
    // reconciled list) has an explicit typed operation, plus the MVP-1 Ledger statement route
    // added afterward — 54 members: 2 diagnostics + 5 companies/session + 12 master-data + 12
    // ledger + 11 stock-item + 6 voucher + 5 reserved stubs + 1 device self-service.
    @Test
    fun `exactly the 54 specified routes are represented, each with a unique path+method`() {
        val operations = allOperations()
        assertEquals(54, operations.size)
        val signatures = operations.map { it.method to it.route() }
        assertEquals("no duplicate (method, path) pairs", signatures.size, signatures.toSet().size)
    }

    // 52. both /api/v1/vouchers and /api/v1/vouchers/search exist independently
    @Test
    fun `the two duplicate voucher-listing routes are both represented independently`() {
        val routes = allOperations().map { it.route() }
        assertTrue(routes.contains("/api/v1/vouchers"))
        assertTrue(routes.contains("/api/v1/vouchers/search"))
    }

    // 53. all five reserved protected stub routes are represented
    @Test
    fun `all five reserved protected stub routes are represented`() {
        val routes = allOperations().map { it.route() }
        assertTrue(routes.contains("/companies/company-1/ledgers/ledger-1"))
        assertTrue(routes.contains("/companies/company-1/ledger-transactions"))
        assertTrue(routes.contains("/companies/company-1/vouchers"))
        assertTrue(routes.contains("/companies/company-1/vouchers/voucher-1"))
        assertTrue(routes.contains("/sync/checkpoint"))
    }

    // 54. all ledger sync/status/storage routes are represented
    @Test
    fun `all twelve ledger routes are represented`() {
        val routes = allOperations().map { it.route() }
        listOf(
            "/ledgers", "/ledgers/ledger-1", "/ledgers/ledger-1/statement", "/sync/ledgers",
            "/sync/ledgers/cancel", "/sync/ledgers/status", "/sync/ledgers/statistics",
            "/sync/ledgers/runs", "/sync/ledgers/runs/run-1", "/sync/ledgers/clear-cache",
            "/storage/ledgers/integrity-check", "/storage/ledgers/backup",
        ).forEach { assertTrue("missing $it", routes.contains(it)) }
    }

    // 55. all stock sync/status/storage routes are represented
    @Test
    fun `all eleven stock-item routes are represented`() {
        val routes = allOperations().map { it.route() }
        listOf(
            "/stock-items", "/stock-items/stock-1", "/sync/stock-items", "/sync/stock-items/cancel",
            "/sync/stock-items/status", "/sync/stock-items/statistics", "/sync/stock-items/runs",
            "/sync/stock-items/runs/run-1", "/sync/stock-items/clear-cache",
            "/storage/stock-items/integrity-check", "/storage/stock-items/backup",
        ).forEach { assertTrue("missing $it", routes.contains(it)) }
    }

    // 56. voucher sync and snapshot routes are represented
    @Test
    fun `voucher sync and snapshot routes are represented`() {
        val routes = allOperations().map { it.route() }
        listOf(
            "/sync/vouchers", "/api/v1/vouchers/snapshots", "/api/v1/vouchers/snapshots/snapshot-1",
            "/api/v1/vouchers/voucher-1",
        ).forEach { assertTrue("missing $it", routes.contains(it)) }
    }

    // 57. master-data routes are represented
    @Test
    fun `all twelve company master-data routes are represented`() {
        val routes = allOperations().map { it.route() }
        listOf(
            "/companies/company-1", "/companies/company-1/ledger-groups", "/companies/company-1/ledgers",
            "/companies/company-1/stock-groups", "/companies/company-1/stock-categories",
            "/companies/company-1/stock-items", "/companies/company-1/units", "/companies/company-1/godowns",
            "/companies/company-1/cost-categories", "/companies/company-1/cost-centres",
            "/companies/company-1/voucher-types", "/companies/company-1/gst-registrations",
        ).forEach { assertTrue("missing $it", routes.contains(it)) }
    }

    // 58. public health/readiness routes are excluded
    @Test
    fun `public health and readiness routes are not represented`() {
        val routes = allOperations().map { it.route() }
        assertFalse(routes.contains("/health"))
        assertFalse(routes.contains("/ready"))
    }

    // 59. pairing bootstrap routes are excluded
    @Test
    fun `pairing bootstrap routes are not represented`() {
        val routes = allOperations().map { it.route() }
        assertFalse(routes.any { it.startsWith("/device/pairing-session") })
        assertFalse(routes.any { it.startsWith("/device/pairing-credential") })
    }

    // 60. Desktop-control routes are excluded
    @Test
    fun `Desktop-control-only routes are not represented`() {
        val routes = allOperations().map { it.route() }
        assertFalse(routes.contains("/device/list"))
        assertFalse(routes.any { it.matches(Regex("/device/[^/]+")) && it != "/device/trusted-companies" })
    }

    // 61. legacy /device/pair and /device/validate-token are excluded
    @Test
    fun `legacy compatibility-only device routes are not represented`() {
        val routes = allOperations().map { it.route() }
        assertFalse(routes.contains("/device/pair"))
        assertFalse(routes.contains("/device/validate-token"))
    }

    // Phase 3S-D2. GetVoucherById requires both voucherId and companyId, emits exactly the
    // Connector-mandatory `company` query parameter, and rejects blank arguments at construction
    // — the only typed operation with its own validation, since it is the only one whose
    // Connector route enforces a mandatory (not session-inferred) scope parameter.
    @Test
    fun `GetVoucherById emits exactly the GET path and the mandatory company query parameter`() {
        val operation = AuthenticatedConnectorOperation.GetVoucherById("voucher-1", "company-1")
        assertEquals(ConnectorHttpMethod.GET, operation.method)
        assertEquals("/api/v1/vouchers/voucher-1", operation.route())
        assertEquals(mapOf("company" to "company-1"), operation.queryParams)
    }

    @Test
    fun `GetVoucherById rejects a blank voucherId`() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthenticatedConnectorOperation.GetVoucherById("", "company-1")
        }
    }

    @Test
    fun `GetVoucherById rejects a blank companyId`() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthenticatedConnectorOperation.GetVoucherById("voucher-1", "")
        }
    }

    // 62. arbitrary raw URLs cannot be supplied — the port's only entry point takes a typed
    // AuthenticatedConnectorOperation, never a String/URL.
    @Test
    fun `the port exposes no raw-URL execution method`() {
        val executeMethod = AuthenticatedConnectorApiPort::class.java.methods.single { it.name == "execute" }
        val paramType = executeMethod.parameterTypes.first()
        assertEquals(AuthenticatedConnectorOperation::class.java, paramType)
        assertTrue(
            "AuthenticatedConnectorApiPort must expose no method accepting a raw String/URL",
            AuthenticatedConnectorApiPort::class.java.methods.none { method ->
                method.parameterTypes.any { it == String::class.java || it == java.net.URL::class.java }
            },
        )
    }
}
