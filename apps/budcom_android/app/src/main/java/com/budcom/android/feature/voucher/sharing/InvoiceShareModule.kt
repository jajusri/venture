package com.budcom.android.feature.voucher.sharing

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class InvoiceShareModule {
    @Binds
    abstract fun bindInvoiceShareCoordinator(
        implementation: AndroidInvoiceShareCoordinator,
    ): InvoiceShareCoordinator
}
