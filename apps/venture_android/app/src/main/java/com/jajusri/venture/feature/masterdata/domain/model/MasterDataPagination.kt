package com.jajusri.venture.feature.masterdata.domain.model

/**
 * Shared pagination metadata matching Connector list envelopes
 * (`page`, `pageSize`, `totalItems`, `totalPages`).
 */
data class MasterDataPagination(
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
) {
    val canLoadMore: Boolean get() = page < totalPages
}
