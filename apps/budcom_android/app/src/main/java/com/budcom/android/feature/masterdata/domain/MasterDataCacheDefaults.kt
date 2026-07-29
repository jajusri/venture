package com.budcom.android.feature.masterdata.domain

/**
 * Bounds for warming the durable Room cache from paginated Connector GETs.
 * Full snapshot replace runs only when every page in range succeeds.
 */
object MasterDataCacheDefaults {
    const val MAX_SNAPSHOT_PAGES: Int = 100
    const val SNAPSHOT_PAGE_SIZE: Int = 100
}
