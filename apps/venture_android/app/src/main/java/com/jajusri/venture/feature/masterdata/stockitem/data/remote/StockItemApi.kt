package com.jajusri.venture.feature.masterdata.stockitem.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Confirmed Connector stock item list endpoint: `GET /stock-items`.
 */
interface StockItemApi {
    @GET("stock-items")
    suspend fun getStockItems(
        @Query("query") query: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
        @Query("sortBy") sortBy: String = "name",
        @Query("sortDirection") sortDirection: String = "asc",
    ): StockItemListResponseDto
}
