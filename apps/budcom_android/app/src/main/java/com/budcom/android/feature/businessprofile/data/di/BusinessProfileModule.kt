package com.budcom.android.feature.businessprofile.data.di

import com.budcom.android.feature.businessprofile.data.repository.BusinessProfileRepositoryImpl
import com.budcom.android.feature.businessprofile.domain.repository.BusinessProfileRepository
import com.budcom.android.feature.businessprofile.storage.AndroidBusinessProfileLogoStore
import com.budcom.android.feature.businessprofile.storage.BusinessProfileLogoStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BusinessProfileBindModule {

    @Binds
    @Singleton
    abstract fun bindBusinessProfileRepository(impl: BusinessProfileRepositoryImpl): BusinessProfileRepository

    @Binds
    @Singleton
    abstract fun bindBusinessProfileLogoStore(impl: AndroidBusinessProfileLogoStore): BusinessProfileLogoStore
}
