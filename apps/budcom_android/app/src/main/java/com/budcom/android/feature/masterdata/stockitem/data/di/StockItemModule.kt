package com.budcom.android.feature.masterdata.stockitem.data.di

import com.budcom.android.feature.masterdata.stockitem.data.local.RoomStockItemLocalDataSource
import com.budcom.android.feature.masterdata.stockitem.data.local.StockItemLocalDataSource
import com.budcom.android.feature.masterdata.stockitem.data.remote.DefaultStockItemRemoteDataSource
import com.budcom.android.feature.masterdata.stockitem.data.remote.StockItemApi
import com.budcom.android.feature.masterdata.stockitem.data.remote.StockItemRemoteDataSource
import com.budcom.android.feature.masterdata.stockitem.data.repository.SearchStockItemsPortImpl
import com.budcom.android.feature.masterdata.stockitem.data.repository.StockItemRepositoryImpl
import com.budcom.android.feature.masterdata.stockitem.domain.port.SearchStockItemsPort
import com.budcom.android.feature.masterdata.stockitem.domain.repository.StockItemRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StockItemBindModule {

    @Binds
    @Singleton
    abstract fun bindStockItemRemoteDataSource(
        impl: DefaultStockItemRemoteDataSource,
    ): StockItemRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindStockItemLocalDataSource(
        impl: RoomStockItemLocalDataSource,
    ): StockItemLocalDataSource

    @Binds
    @Singleton
    abstract fun bindStockItemRepository(
        impl: StockItemRepositoryImpl,
    ): StockItemRepository

    @Binds
    @Singleton
    abstract fun bindSearchStockItemsPort(
        impl: SearchStockItemsPortImpl,
    ): SearchStockItemsPort
}

@Module
@InstallIn(SingletonComponent::class)
object StockItemProvideModule {

    @Provides
    @Singleton
    fun provideStockItemApi(retrofit: Retrofit): StockItemApi =
        retrofit.create(StockItemApi::class.java)
}
