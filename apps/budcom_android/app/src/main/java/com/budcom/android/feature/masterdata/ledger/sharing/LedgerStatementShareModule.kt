package com.budcom.android.feature.masterdata.ledger.sharing

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LedgerStatementShareModule {
    @Binds
    abstract fun bindLedgerStatementShareCoordinator(
        implementation: AndroidLedgerStatementShareCoordinator,
    ): LedgerStatementShareCoordinator
}
