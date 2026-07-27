package com.budcom.android.feature.voucher.data.di

import com.budcom.android.feature.voucher.data.remote.DefaultVoucherRemoteDataSource
import com.budcom.android.feature.voucher.data.remote.VoucherApi
import com.budcom.android.feature.voucher.data.remote.VoucherRemoteDataSource
import com.budcom.android.feature.voucher.data.repository.VoucherRepositoryImpl
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
    abstract fun bindVoucherRepository(
        impl: VoucherRepositoryImpl,
    ): VoucherRepository
}

@Module
@InstallIn(SingletonComponent::class)
object VoucherProvideModule {

    @Provides
    @Singleton
    fun provideVoucherApi(retrofit: Retrofit): VoucherApi =
        retrofit.create(VoucherApi::class.java)
}
