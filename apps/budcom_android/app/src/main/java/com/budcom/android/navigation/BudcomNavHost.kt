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
                onOpenVouchers = { navController.navigate(Routes.VOUCHERS) },
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
                onOpenLedgers = { navController.navigate(Routes.LEDGERS) },
                onOpenStockItems = { navController.navigate(Routes.STOCK_ITEMS) },
            )
        }
        composable(route = Routes.LEDGERS) {
            LedgerBrowserRoute()
        }
        composable(route = Routes.STOCK_ITEMS) {
            StockItemBrowserRoute()
        }
        composable(route = Routes.VOUCHERS) {
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
