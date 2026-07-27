package com.budcom.android.feature.masterdata.domain

/**
 * Shared list-browser conventions for Master Data categories.
 * Entity-specific repositories and DTOs remain typed per slice.
 */
object MasterDataBrowserDefaults {
    const val SEARCH_DEBOUNCE_MS = 350L
    const val DEFAULT_PAGE_SIZE = 50
    const val MAX_PAGE_SIZE = 100
    const val MAX_QUERY_LENGTH = 128
}
