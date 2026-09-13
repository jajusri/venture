package com.jajusri.venture.feature.company.data.repository

import com.jajusri.venture.feature.company.data.local.CompanyLocalDataSource
import com.jajusri.venture.feature.company.data.local.RoomCompanyLocalDataSource
import com.jajusri.venture.feature.company.data.remote.AuthenticatedCompanyRemoteDataSource
import com.jajusri.venture.feature.company.data.remote.CompanyApi
import com.jajusri.venture.feature.company.data.remote.CompanyRemoteDataSource
import com.jajusri.venture.feature.company.data.remote.DefaultAuthenticatedCompanyRemoteDataSource
import com.jajusri.venture.feature.company.data.remote.DefaultCompanyRemoteDataSource
import com.jajusri.venture.feature.company.domain.repository.CompanyRepository
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
    abstract fun bindAuthenticatedCompanyRemoteDataSource(
        impl: DefaultAuthenticatedCompanyRemoteDataSource,
    ): AuthenticatedCompanyRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindCompanyLocalDataSource(
        impl: RoomCompanyLocalDataSource,
    ): CompanyLocalDataSource

    @Binds
    @Singleton
    abstract fun bindCompanyRepository(
        impl: CompanyRepositoryImpl,
    ): CompanyRepository

    @Binds
    @Singleton
    abstract fun bindCompanySessionPort(
        impl: CompanySessionPortImpl,
    ): com.jajusri.venture.feature.company.domain.port.CompanySessionPort

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
