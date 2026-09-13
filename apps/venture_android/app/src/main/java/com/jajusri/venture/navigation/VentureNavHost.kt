package com.jajusri.venture.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jajusri.venture.feature.businessprofile.presentation.BusinessProfileRoute
import com.jajusri.venture.feature.catalogue.presentation.CatalogueDetailRoute
import com.jajusri.venture.feature.catalogue.presentation.CatalogueDetailViewModel
import com.jajusri.venture.feature.catalogue.presentation.CatalogueRoute
import com.jajusri.venture.feature.catalogue.presentation.CatalogueStockItemPickerRoute
import com.jajusri.venture.feature.company.presentation.CompanyRoute
import com.jajusri.venture.feature.connect.presentation.ConnectRoute
import com.jajusri.venture.feature.connect.presentation.PartyDetailRoute
import com.jajusri.venture.feature.connect.presentation.PartyDetailViewModel
import com.jajusri.venture.feature.connect.presentation.PartyXmlExportRoute
import com.jajusri.venture.feature.connect.presentation.PartyXmlExportViewModel
import com.jajusri.venture.feature.connect.presentation.ProspectCreateRoute
import com.jajusri.venture.feature.dashboard.presentation.DashboardRoute
import com.jajusri.venture.feature.diagnostics.presentation.DiagnosticsRoute
import com.jajusri.venture.feature.dincharya.presentation.DincharyaRoute
import com.jajusri.venture.feature.discovery.presentation.ConnectorDiscoveryRoute
import com.jajusri.venture.feature.masterdata.ledger.presentation.LedgerBrowserRoute
import com.jajusri.venture.feature.masterdata.ledger.presentation.LedgerStatementRoute
import com.jajusri.venture.feature.masterdata.ledger.presentation.LedgerStatementViewModel
import com.jajusri.venture.feature.masterdata.presentation.MasterDataHubScreen
import com.jajusri.venture.feature.masterdata.stockitem.presentation.StockItemBrowserRoute
import com.jajusri.venture.feature.pairing.presentation.SecurePairingRoute
import com.jajusri.venture.feature.search.presentation.UniversalSearchRoute
import com.jajusri.venture.feature.serverconfig.presentation.ServerConfigRoute
import com.jajusri.venture.feature.settings.presentation.SettingsRoute
import com.jajusri.venture.core.trust.presentation.OperationalStatusRoute
import com.jajusri.venture.feature.sync.presentation.SyncRoute
import com.jajusri.venture.feature.transaction.presentation.ReceivedOrderRoute
import com.jajusri.venture.feature.transaction.presentation.ReceivedOrderViewModel
import com.jajusri.venture.feature.transaction.presentation.SellerRevisionRoute
import com.jajusri.venture.feature.transaction.presentation.SellerRevisionViewModel
import com.jajusri.venture.feature.transaction.presentation.ReceivedRevisionRoute
import com.jajusri.venture.feature.transaction.presentation.ReceivedRevisionViewModel
import com.jajusri.venture.feature.transaction.presentation.StructuredRecipientInboxRoute
import com.jajusri.venture.feature.transaction.presentation.TransactionComposerRoute
import com.jajusri.venture.feature.voucher.presentation.VoucherBrowserRoute
import com.jajusri.venture.feature.voucher.presentation.VoucherDetailsRoute
import com.jajusri.venture.feature.voucher.presentation.VoucherDetailsViewModel
import com.jajusri.venture.ui.components.FullScreenLoading

/**
 * Root navigation host for VENTURE Android.
 *
 * The start destination is resolved once via [AppRootViewModel] from the single authoritative
 * [StartupRoutingState]: secure pairing (bootstrap or resume) when it is required or already in
 * flight, Dashboard otherwise — including for an existing legacy installation, an ACTIVE secure
 * credential, and a RE_PAIR_REQUIRED/credential-unavailable installation whose cached data must
 * remain reachable. This keeps Dashboard (and the business API calls its ViewModel fires on load)
 * from ever composing before that classification completes.
 */
@Composable
fun VentureNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    rootViewModel: AppRootViewModel = hiltViewModel(),
) {
    val startDestination by rootViewModel.startDestination.collectAsStateWithLifecycle()
    val resolvedStartDestination = startDestination
    if (resolvedStartDestination == null) {
        FullScreenLoading(modifier = modifier.fillMaxSize())
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
                onOpenStatus = {
                    // Deliberately no popUpTo: pairing is not abandoned, just set aside -- back
                    // navigation from Dashboard returns here so pairing remains resumable. Safe to
                    // compose Dashboard here (unlike at NavHost start) because pairing classification
                    // has already resolved by the time this screen -- and this explicit user tap --
                    // exist at all.
                    navController.navigate(Routes.HOME)
                },
            )
        }
        composable(route = Routes.HOME) {
            DashboardRoute(
                onOpenServerConfig = { navController.navigate(Routes.SERVER_CONFIG) },
                onOpenCompanySelection = { navController.navigate(Routes.COMPANY) },
                onOpenMasterData = { navController.navigate(Routes.MASTER_DATA) },
                onOpenVouchers = { navController.navigate(Routes.vouchers()) },
                onOpenLedgers = { navController.navigate(Routes.ledgers()) },
                onOpenConnect = { navController.navigate(Routes.connect()) },
                onOpenDincharya = { navController.navigate(Routes.DINCHARYA) },
                onOpenBusinessProfile = { navController.navigate(Routes.BUSINESS_PROFILE) },
                onOpenCatalogue = { navController.navigate(Routes.CATALOGUE) },
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
                onOpenStructuredInbox = { navController.navigate(Routes.STRUCTURED_INBOX) },
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
                onOpenTrustStatus = { navController.navigate(Routes.TRUST_STATUS) },
            )
        }
        composable(route = Routes.TRUST_STATUS) {
            OperationalStatusRoute(onBack = { navController.popBackStack() })
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
            VoucherDetailsRoute(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.CONNECT,
            arguments = listOf(
                navArgument(Routes.QUERY_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            ConnectRoute(
                onOpenLedgerStatement = { ledgerId ->
                    navController.navigate(Routes.ledgerStatement(ledgerId))
                },
                onOpenVouchers = { query ->
                    navController.navigate(Routes.vouchers(query))
                },
                onOpenPartyDetail = { partyId ->
                    navController.navigate(Routes.partyDetail(partyId))
                },
                onOpenProspectCreate = {
                    navController.navigate(Routes.PROSPECT_CREATE)
                },
            )
        }
        composable(route = Routes.DINCHARYA) {
            DincharyaRoute(
                onOpenPartyDetail = { partyId ->
                    navController.navigate(Routes.partyDetail(partyId))
                },
            )
        }
        composable(route = Routes.BUSINESS_PROFILE) {
            BusinessProfileRoute()
        }
        composable(route = Routes.CATALOGUE) {
            CatalogueRoute(
                onOpenProductDetail = { productId ->
                    navController.navigate(Routes.catalogueDetail(productId))
                },
                onOpenStockItemPicker = { navController.navigate(Routes.CATALOGUE_STOCK_ITEM_PICKER) },
                onOpenTransactionComposer = { navController.navigate(Routes.TRANSACTION_COMPOSER) },
            )
        }
        composable(route = Routes.TRANSACTION_COMPOSER) {
            TransactionComposerRoute(
                onOpenStructuredInbox = { navController.navigate(Routes.STRUCTURED_INBOX) },
            )
        }
        composable(route = Routes.STRUCTURED_INBOX) {
            StructuredRecipientInboxRoute(
                onOpenRoute = { route -> navController.navigate(route) },
            )
        }
        composable(
            route = Routes.RECEIVED_ORDER,
            arguments = listOf(
                navArgument(ReceivedOrderViewModel.ENVELOPE_ID_ARG) { type = NavType.StringType },
                navArgument(ReceivedOrderViewModel.SENDER_BUSINESS_ID_ARG) { type = NavType.StringType },
                navArgument(ReceivedOrderViewModel.ORDER_ID_ARG) { type = NavType.StringType },
                navArgument(ReceivedOrderViewModel.ORDER_VERSION_ARG) { type = NavType.IntType },
            ),
        ) { backStack ->
            ReceivedOrderRoute(
                onRevise = {
                    val args = backStack.arguments
                    val envelopeId = args?.getString(ReceivedOrderViewModel.ENVELOPE_ID_ARG)
                    val sender = args?.getString(ReceivedOrderViewModel.SENDER_BUSINESS_ID_ARG)
                    val orderId = args?.getString(ReceivedOrderViewModel.ORDER_ID_ARG)
                    val version = args?.getInt(ReceivedOrderViewModel.ORDER_VERSION_ARG)
                    if (envelopeId != null && sender != null && orderId != null && version != null) {
                        navController.navigate(Routes.sellerRevision(envelopeId, sender, orderId, version))
                    }
                },
            )
        }
        composable(
            route = Routes.SELLER_REVISION,
            arguments = listOf(
                navArgument(SellerRevisionViewModel.ENVELOPE_ID_ARG) { type = NavType.StringType },
                navArgument(SellerRevisionViewModel.BUYER_BUSINESS_ID_ARG) { type = NavType.StringType },
                navArgument(SellerRevisionViewModel.ORDER_ID_ARG) { type = NavType.StringType },
                navArgument(SellerRevisionViewModel.ORDER_VERSION_ARG) { type = NavType.IntType },
            ),
        ) {
            SellerRevisionRoute()
        }
        composable(
            route = Routes.RECEIVED_REVISION,
            arguments = listOf(
                navArgument(ReceivedRevisionViewModel.ENVELOPE_ID_ARG) { type = NavType.StringType },
                navArgument(ReceivedRevisionViewModel.SENDER_BUSINESS_ID_ARG) { type = NavType.StringType },
                navArgument(ReceivedRevisionViewModel.ORDER_ID_ARG) { type = NavType.StringType },
                navArgument(ReceivedRevisionViewModel.ORDER_VERSION_ARG) { type = NavType.IntType },
            ),
        ) {
            ReceivedRevisionRoute()
        }
        composable(
            route = Routes.CATALOGUE_DETAIL,
            arguments = listOf(
                navArgument(CatalogueDetailViewModel.PRODUCT_ID_ARG) {
                    type = NavType.StringType
                },
            ),
        ) {
            CatalogueDetailRoute(onBack = { navController.popBackStack() })
        }
        composable(route = Routes.CATALOGUE_STOCK_ITEM_PICKER) {
            CatalogueStockItemPickerRoute(
                onLinked = { productId ->
                    navController.navigate(Routes.catalogueDetail(productId)) {
                        popUpTo(Routes.CATALOGUE_STOCK_ITEM_PICKER) { inclusive = true }
                    }
                },
                onLinkedAll = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.PARTY_DETAIL,
            arguments = listOf(
                navArgument(PartyDetailViewModel.PARTY_ID_ARG) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val partyId = backStackEntry.arguments?.getString(PartyDetailViewModel.PARTY_ID_ARG)
            PartyDetailRoute(
                onOpenLedgerStatement = { ledgerId ->
                    navController.navigate(Routes.ledgerStatement(ledgerId))
                },
                onOpenVouchers = { query ->
                    navController.navigate(Routes.vouchers(query))
                },
                onOpenVoucherDetails = { voucherId ->
                    navController.navigate(Routes.voucherDetails(voucherId))
                },
                onOpenXmlExport = {
                    if (partyId != null) navController.navigate(Routes.partyXmlExport(partyId))
                },
            )
        }
        composable(route = Routes.PROSPECT_CREATE) {
            ProspectCreateRoute(
                onBack = { navController.popBackStack() },
                onCreated = { partyId ->
                    navController.navigate(Routes.partyDetail(partyId)) {
                        popUpTo(Routes.PROSPECT_CREATE) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.PARTY_XML_EXPORT,
            arguments = listOf(
                navArgument(PartyXmlExportViewModel.PARTY_ID_ARG) {
                    type = NavType.StringType
                },
            ),
        ) {
            PartyXmlExportRoute()
        }
    }
}
