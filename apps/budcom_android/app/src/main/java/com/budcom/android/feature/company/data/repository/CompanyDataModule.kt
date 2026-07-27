package com.budcom.android.feature.company.data.repository

import com.budcom.android.feature.company.data.remote.CompanyApi
import com.budcom.android.feature.company.data.remote.CompanyRemoteDataSource
import com.budcom.android.feature.company.data.remote.DefaultCompanyRemoteDataSource
import com.budcom.android.feature.company.domain.repository.CompanyRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CompanyBindModule {
    @Binds
    @Singleton
    abstract fun bindCompanyRemoteDataSource(
        impl: DefaultCompanyRemoteDataSource,
    ): CompanyRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindCompanyRepository(
        impl: CompanyRepositoryImpl,
    ): CompanyRepository

    @Binds
    @Singleton
    abstract fun bindCompanySessionPort(
        impl: CompanySessionPortImpl,
    ): com.budcom.android.feature.company.domain.port.CompanySessionPort

    @Binds
    @Singleton
    abstract fun bindSelectedCompanyStore(
        impl: DataStoreSelectedCompanyStore,
    ): SelectedCompanyStore
}

@Module
@InstallIn(SingletonComponent::class)
object CompanyProvideModule {
    @Provides
    @Singleton
    fun provideCompanyApi(retrofit: Retrofit): CompanyApi = retrofit.create(CompanyApi::class.java)
}
