package com.budcom.android.feature.masterdata.ledger.data.di

import com.budcom.android.feature.masterdata.ledger.data.local.LedgerStatementLocalDataSource
import com.budcom.android.feature.masterdata.ledger.data.local.RoomLedgerStatementLocalDataSource
import com.budcom.android.feature.masterdata.ledger.data.remote.AuthenticatedLedgerStatementRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.data.remote.DefaultAuthenticatedLedgerStatementRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.data.repository.LedgerStatementRepositoryImpl
import com.budcom.android.feature.masterdata.ledger.domain.repository.LedgerStatementRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LedgerStatementBindModule {

    @Binds
    @Singleton
    abstract fun bindAuthenticatedLedgerStatementRemoteDataSource(
        impl: DefaultAuthenticatedLedgerStatementRemoteDataSource,
    ): AuthenticatedLedgerStatementRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindLedgerStatementLocalDataSource(
        impl: RoomLedgerStatementLocalDataSource,
    ): LedgerStatementLocalDataSource

    @Binds
    @Singleton
    abstract fun bindLedgerStatementRepository(
        impl: LedgerStatementRepositoryImpl,
    ): LedgerStatementRepository
}
