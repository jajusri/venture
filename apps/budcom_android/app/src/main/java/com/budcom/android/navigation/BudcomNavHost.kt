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
import com.budcom.android.feature.masterdata.presentation.MasterDataHubScreen
import com.budcom.android.feature.masterdata.stockitem.presentation.StockItemBrowserRoute
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
 * The start destination is resolved once via [AppRootViewModel]: first-install Connector
 * discovery when nothing is paired yet on a physical device, Dashboard otherwise. This keeps
 * Dashboard (and the business API calls its ViewModel fires on load) from ever composing
 * before a Connector is paired.
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
            LedgerBrowserRoute()
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
