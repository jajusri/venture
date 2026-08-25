package com.budcom.android.feature.transaction.data.di

import com.budcom.android.feature.transaction.data.TransactionClockImpl
import com.budcom.android.feature.transaction.data.port.LocalTransactionSubmissionPort
import com.budcom.android.feature.transaction.data.repository.TransactionRepositoryImpl
import com.budcom.android.feature.transaction.domain.model.TransactionClock
import com.budcom.android.feature.transaction.domain.port.NoOpTransactionReminderScheduler
import com.budcom.android.feature.transaction.domain.port.TransactionReminderScheduler
import com.budcom.android.feature.transaction.domain.port.TransactionSubmissionPort
import com.budcom.android.feature.transaction.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * [bindTransactionSubmissionPort] and [bindTransactionReminderScheduler] are the two bindings that
 * matter most here: swapping either to a real implementation later (a genuine cross-company
 * transport, a real WorkManager-based scheduler) is a one-line change to this module, with zero
 * change anywhere in [TransactionRepositoryImpl] or the domain layer — that is the entire point of
 * routing both through ports (architecture Findings 1/2).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TransactionBindModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindTransactionClock(impl: TransactionClockImpl): TransactionClock

    @Binds
    @Singleton
    abstract fun bindTransactionSubmissionPort(impl: LocalTransactionSubmissionPort): TransactionSubmissionPort

    @Binds
    @Singleton
    abstract fun bindTransactionReminderScheduler(impl: NoOpTransactionReminderScheduler): TransactionReminderScheduler
}
