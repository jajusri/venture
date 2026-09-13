package com.jajusri.venture.feature.voucher.data.di

import com.jajusri.venture.feature.voucher.data.remote.AuthenticatedVoucherDetailRemoteDataSource
import com.jajusri.venture.feature.voucher.data.remote.AuthenticatedVoucherListRemoteDataSource
import com.jajusri.venture.feature.voucher.data.remote.DefaultAuthenticatedVoucherDetailRemoteDataSource
import com.jajusri.venture.feature.voucher.data.remote.DefaultAuthenticatedVoucherListRemoteDataSource
import com.jajusri.venture.feature.voucher.data.remote.DefaultVoucherRemoteDataSource
import com.jajusri.venture.feature.voucher.data.remote.VoucherApi
import com.jajusri.venture.feature.voucher.data.remote.VoucherRefreshTimeoutPolicy
import com.jajusri.venture.feature.voucher.data.remote.VoucherRemoteDataSource
import com.jajusri.venture.feature.voucher.data.local.RoomVoucherLocalDataSource
import com.jajusri.venture.feature.voucher.data.local.VoucherLocalDataSource
import com.jajusri.venture.feature.voucher.data.repository.SearchVouchersPortImpl
import com.jajusri.venture.feature.voucher.data.repository.VoucherRepositoryImpl
import com.jajusri.venture.feature.voucher.domain.port.SearchVouchersPort
import com.jajusri.venture.feature.voucher.domain.repository.VoucherRepository
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
