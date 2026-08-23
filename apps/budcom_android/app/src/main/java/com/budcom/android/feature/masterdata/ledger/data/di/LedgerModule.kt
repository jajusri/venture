package com.budcom.android.feature.masterdata.ledger.data.di

import com.budcom.android.feature.masterdata.ledger.data.local.LedgerLocalDataSource
import com.budcom.android.feature.masterdata.ledger.data.local.RoomLedgerLocalDataSource
import com.budcom.android.feature.masterdata.ledger.data.remote.AuthenticatedLedgerRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.data.remote.DefaultAuthenticatedLedgerRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.data.remote.DefaultLedgerRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.data.remote.LedgerApi
import com.budcom.android.feature.masterdata.ledger.data.remote.LedgerRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.data.repository.LedgerBulkContactDetailPortImpl
import com.budcom.android.feature.masterdata.ledger.data.repository.LedgerLiveDetailPortImpl
import com.budcom.android.feature.masterdata.ledger.data.repository.LedgerRepositoryImpl
import com.budcom.android.feature.masterdata.ledger.data.repository.LedgerSnapshotPortImpl
import com.budcom.android.feature.masterdata.ledger.data.repository.SearchLedgersPortImpl
import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerBulkContactDetailPort
import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerLiveDetailPort
import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerSnapshotPort
import com.budcom.android.feature.masterdata.ledger.domain.port.SearchLedgersPort
import com.budcom.android.feature.masterdata.ledger.domain.repository.LedgerRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LedgerBindModule {

    @Binds
    @Singleton
    abstract fun bindLedgerRemoteDataSource(
        impl: DefaultLedgerRemoteDataSource,
    ): LedgerRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindAuthenticatedLedgerRemoteDataSource(
        impl: DefaultAuthenticatedLedgerRemoteDataSource,
    ): AuthenticatedLedgerRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindLedgerLocalDataSource(
        impl: RoomLedgerLocalDataSource,
    ): LedgerLocalDataSource

    @Binds
    @Singleton
    abstract fun bindLedgerRepository(
        impl: LedgerRepositoryImpl,
    ): LedgerRepository

    @Binds
    @Singleton
    abstract fun bindSearchLedgersPort(
        impl: SearchLedgersPortImpl,
    ): SearchLedgersPort

    @Binds
    @Singleton
    abstract fun bindLedgerSnapshotPort(
        impl: LedgerSnapshotPortImpl,
    ): LedgerSnapshotPort

    @Binds
    @Singleton
    abstract fun bindLedgerLiveDetailPort(
        impl: LedgerLiveDetailPortImpl,
    ): LedgerLiveDetailPort

    @Binds
    @Singleton
    abstract fun bindLedgerBulkContactDetailPort(
        impl: LedgerBulkContactDetailPortImpl,
    ): LedgerBulkContactDetailPort
}

@Module
@InstallIn(SingletonComponent::class)
object LedgerProvideModule {

    @Provides
    @Singleton
    fun provideLedgerApi(retrofit: Retrofit): LedgerApi =
        retrofit.create(LedgerApi::class.java)
}
