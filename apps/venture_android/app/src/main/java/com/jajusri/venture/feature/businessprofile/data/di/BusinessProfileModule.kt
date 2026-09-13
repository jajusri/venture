package com.jajusri.venture.feature.businessprofile.data.di

import com.jajusri.venture.feature.businessprofile.data.repository.BusinessProfileRepositoryImpl
import com.jajusri.venture.feature.businessprofile.domain.repository.BusinessProfileRepository
import com.jajusri.venture.feature.businessprofile.storage.AndroidBusinessProfileLogoStore
import com.jajusri.venture.feature.businessprofile.storage.BusinessProfileLogoStore
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
