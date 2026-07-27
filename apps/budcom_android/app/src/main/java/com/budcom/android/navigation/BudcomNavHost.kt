package com.budcom.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.budcom.android.feature.company.presentation.CompanyRoute
import com.budcom.android.feature.dashboard.presentation.DashboardRoute
import com.budcom.android.feature.masterdata.ledger.presentation.LedgerBrowserRoute
import com.budcom.android.feature.masterdata.presentation.MasterDataHubScreen
import com.budcom.android.feature.masterdata.stockitem.presentation.StockItemBrowserRoute
import com.budcom.android.feature.search.presentation.UniversalSearchRoute
import com.budcom.android.feature.serverconfig.presentation.ServerConfigRoute
import com.budcom.android.feature.voucher.presentation.VoucherBrowserRoute
import com.budcom.android.feature.voucher.presentation.VoucherDetailsRoute
import com.budcom.android.feature.voucher.presentation.VoucherDetailsViewModel

/**
 * Root navigation host for BUDCO Android.
 */
@Composable
fun BudcomNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(route = Routes.HOME) {
            DashboardRoute(
                onOpenServerConfig = { navController.navigate(Routes.SERVER_CONFIG) },
                onOpenCompanySelection = { navController.navigate(Routes.COMPANY) },
                onOpenMasterData = { navController.navigate(Routes.MASTER_DATA) },
                onOpenVouchers = { navController.navigate(Routes.vouchers()) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
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
