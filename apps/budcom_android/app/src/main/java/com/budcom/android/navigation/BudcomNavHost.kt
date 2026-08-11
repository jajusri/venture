package com.budcom.android.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.budcom.android.feature.company.presentation.CompanyRoute
import com.budcom.android.feature.dashboard.presentation.DashboardRoute
import com.budcom.android.feature.diagnostics.presentation.DiagnosticsRoute
import com.budcom.android.feature.discovery.presentation.ConnectorDiscoveryRoute
import com.budcom.android.feature.masterdata.ledger.presentation.LedgerBrowserRoute
import com.budcom.android.feature.masterdata.ledger.presentation.LedgerStatementRoute
import com.budcom.android.feature.masterdata.ledger.presentation.LedgerStatementViewModel
import com.budcom.android.feature.masterdata.presentation.MasterDataHubScreen
import com.budcom.android.feature.masterdata.stockitem.presentation.StockItemBrowserRoute
import com.budcom.android.feature.pairing.presentation.SecurePairingRoute
import com.budcom.android.feature.search.presentation.UniversalSearchRoute
import com.budcom.android.feature.serverconfig.presentation.ServerConfigRoute
import com.budcom.android.feature.settings.presentation.SettingsRoute
import com.budcom.android.feature.sync.presentation.SyncRoute
import com.budcom.android.feature.voucher.presentation.VoucherBrowserRoute
import com.budcom.android.feature.voucher.presentation.VoucherDetailsRoute
import com.budcom.android.feature.voucher.presentation.VoucherDetailsViewModel

/**
 * Root navigation host for BUDCO Android.
 *
 * The start destination is resolved once via [AppRootViewModel] from the single authoritative
 * [StartupRoutingState]: secure pairing (bootstrap or resume) when it is required or already in
 * flight, Dashboard otherwise — including for an existing legacy installation, an ACTIVE secure
 * credential, and a RE_PAIR_REQUIRED/credential-unavailable installation whose cached data must
 * remain reachable. This keeps Dashboard (and the business API calls its ViewModel fires on load)
 * from ever composing before that classification completes.
 */
@Composable
fun BudcomNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    rootViewModel: AppRootViewModel = hiltViewModel(),
) {
    val startDestination by rootViewModel.startDestination.collectAsStateWithLifecycle()
    val resolvedStartDestination = startDestination
    if (resolvedStartDestination == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    NavHost(
        navController = navController,
        startDestination = resolvedStartDestination,
        modifier = modifier,
    ) {
        composable(route = Routes.CONNECTOR_DISCOVERY) {
            ConnectorDiscoveryRoute(
                onEnrolmentComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.CONNECTOR_DISCOVERY) { inclusive = true }
                    }
                },
                onOpenServerConfig = { navController.navigate(Routes.SERVER_CONFIG) },
            )
        }
        composable(route = Routes.SECURE_PAIRING) {
            SecurePairingRoute(
                onPairingCompleted = {
                    // Clear the pairing flow only after a verified pairing operation completes.
                    // Merely opening this destination with an existing ACTIVE credential must keep
                    // its management UI visible so explicit replacement remains reachable.
                    navController.navigate(Routes.HOME) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                },
            )
        }
        composable(route = Routes.HOME) {
            DashboardRoute(
                onOpenServerConfig = { navController.navigate(Routes.SERVER_CONFIG) },
                onOpenCompanySelection = { navController.navigate(Routes.COMPANY) },
                onOpenMasterData = { navController.navigate(Routes.MASTER_DATA) },
                onOpenVouchers = { navController.navigate(Routes.vouchers()) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSync = { navController.navigate(Routes.SYNC) },
                onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(route = Routes.SERVER_CONFIG) {
            ServerConfigRoute()
        }
        composable(route = Routes.COMPANY) {
            CompanyRoute()
        }
        composable(route = Routes.MASTER_DATA) {
            MasterDataHubScreen(
                onOpenLedgers = { navController.navigate(Routes.ledgers()) },
                onOpenStockItems = { navController.navigate(Routes.stockItems()) },
            )
        }
        composable(route = Routes.SEARCH) {
            UniversalSearchRoute(
                onOpenLedgerBrowser = { query ->
                    navController.navigate(Routes.ledgers(query))
                },
                onOpenStockItemBrowser = { query ->
                    navController.navigate(Routes.stockItems(query))
                },
                onOpenVoucherBrowser = { query ->
                    navController.navigate(Routes.vouchers(query))
                },
                onOpenVoucherDetails = { voucherId ->
                    navController.navigate(Routes.voucherDetails(voucherId))
                },
            )
        }
        composable(route = Routes.SYNC) {
            SyncRoute(
                onOpenCompanySelection = { navController.navigate(Routes.COMPANY) },
                onOpenServerConfig = { navController.navigate(Routes.SERVER_CONFIG) },
            )
        }
        composable(route = Routes.DIAGNOSTICS) {
            DiagnosticsRoute(
                onOpenServerConfig = { navController.navigate(Routes.SERVER_CONFIG) },
                onOpenCompanySelection = { navController.navigate(Routes.COMPANY) },
            )
        }
        composable(route = Routes.SETTINGS) {
            SettingsRoute(
                onOpenServerConfig = { navController.navigate(Routes.SERVER_CONFIG) },
                onOpenCompanySelection = { navController.navigate(Routes.COMPANY) },
                onOpenSync = { navController.navigate(Routes.SYNC) },
                onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                onOpenSecurePairing = { navController.navigate(Routes.SECURE_PAIRING) },
            )
        }
        composable(
            route = Routes.LEDGERS,
            arguments = listOf(
                navArgument(Routes.QUERY_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            LedgerBrowserRoute(
                onOpenLedgerStatement = { ledgerId ->
                    navController.navigate(Routes.ledgerStatement(ledgerId))
                },
            )
        }
        composable(
            route = Routes.LEDGER_STATEMENT,
            arguments = listOf(
                navArgument(LedgerStatementViewModel.LEDGER_ID_ARG) {
                    type = NavType.StringType
                },
            ),
        ) {
            LedgerStatementRoute(
                onBack = { navController.popBackStack() },
                onOpenVoucherDetails = { voucherId ->
                    navController.navigate(Routes.voucherDetails(voucherId))
                },
            )
        }
        composable(
            route = Routes.STOCK_ITEMS,
            arguments = listOf(
                navArgument(Routes.QUERY_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            StockItemBrowserRoute()
        }
        composable(
            route = Routes.VOUCHERS,
            arguments = listOf(
                navArgument(Routes.QUERY_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            VoucherBrowserRoute(
                onOpenVoucherDetails = { voucherId ->
                    navController.navigate(Routes.voucherDetails(voucherId))
                },
            )
        }
        composable(
            route = Routes.VOUCHER_DETAILS,
            arguments = listOf(
                navArgument(VoucherDetailsViewModel.VOUCHER_ID_ARG) {
                    type = NavType.StringType
                },
            ),
        ) {
            VoucherDetailsRoute()
        }
    }
}
