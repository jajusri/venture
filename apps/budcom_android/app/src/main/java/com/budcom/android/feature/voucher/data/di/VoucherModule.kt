package com.budcom.android.feature.voucher.data.di

import com.budcom.android.feature.voucher.data.remote.AuthenticatedVoucherDetailRemoteDataSource
import com.budcom.android.feature.voucher.data.remote.AuthenticatedVoucherListRemoteDataSource
import com.budcom.android.feature.voucher.data.remote.DefaultAuthenticatedVoucherDetailRemoteDataSource
import com.budcom.android.feature.voucher.data.remote.DefaultAuthenticatedVoucherListRemoteDataSource
import com.budcom.android.feature.voucher.data.remote.DefaultVoucherRemoteDataSource
import com.budcom.android.feature.voucher.data.remote.VoucherApi
import com.budcom.android.feature.voucher.data.remote.VoucherRefreshTimeoutPolicy
import com.budcom.android.feature.voucher.data.remote.VoucherRemoteDataSource
import com.budcom.android.feature.voucher.data.local.RoomVoucherLocalDataSource
import com.budcom.android.feature.voucher.data.local.VoucherLocalDataSource
import com.budcom.android.feature.voucher.data.repository.SearchVouchersPortImpl
import com.budcom.android.feature.voucher.data.repository.VoucherRepositoryImpl
import com.budcom.android.feature.voucher.domain.port.SearchVouchersPort
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VoucherBindModule {

    @Binds
    @Singleton
    abstract fun bindVoucherRemoteDataSource(
        impl: DefaultVoucherRemoteDataSource,
    ): VoucherRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindAuthenticatedVoucherListRemoteDataSource(
        impl: DefaultAuthenticatedVoucherListRemoteDataSource,
    ): AuthenticatedVoucherListRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindAuthenticatedVoucherDetailRemoteDataSource(
        impl: DefaultAuthenticatedVoucherDetailRemoteDataSource,
    ): AuthenticatedVoucherDetailRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindVoucherRepository(
        impl: VoucherRepositoryImpl,
    ): VoucherRepository

    @Binds @Singleton
    abstract fun bindVoucherLocalDataSource(impl: RoomVoucherLocalDataSource): VoucherLocalDataSource

    @Binds
    @Singleton
    abstract fun bindSearchVouchersPort(
        impl: SearchVouchersPortImpl,
    ): SearchVouchersPort
}

@Module
@InstallIn(SingletonComponent::class)
object VoucherProvideModule {

    @Provides
    @Singleton
    fun provideVoucherApi(retrofit: Retrofit): VoucherApi =
        retrofit.create(VoucherApi::class.java)

    @Provides
    @Singleton
    fun provideVoucherRefreshTimeoutPolicy(): VoucherRefreshTimeoutPolicy = VoucherRefreshTimeoutPolicy.Default
}
