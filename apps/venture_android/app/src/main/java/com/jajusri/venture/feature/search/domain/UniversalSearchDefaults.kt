package com.jajusri.venture.feature.search.domain

import com.jajusri.venture.feature.masterdata.domain.MasterDataBrowserDefaults
import com.jajusri.venture.feature.voucher.domain.model.VoucherDateRangeDefaults

/**
 * Universal Search foundation defaults.
 *
 * Reuses Master Data debounce / max query length. Preview page size is Search-specific.
 * Connector does not enforce a minimum query length; blank queries are ignored client-side.
 */
object UniversalSearchDefaults {
    const val SEARCH_DEBOUNCE_MS = MasterDataBrowserDefaults.SEARCH_DEBOUNCE_MS
    const val MIN_QUERY_LENGTH = 1
    const val MAX_QUERY_LENGTH = MasterDataBrowserDefaults.MAX_QUERY_LENGTH
    const val PREVIEW_PAGE_SIZE = 5
    const val VOUCHER_LOOKBACK_DAYS = VoucherDateRangeDefaults.DEFAULT_LOOKBACK_DAYS
}
