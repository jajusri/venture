package com.jajusri.venture.feature.dincharya.data.di

import com.jajusri.venture.feature.dincharya.data.repository.DincharyaRepositoryImpl
import com.jajusri.venture.feature.dincharya.domain.repository.DincharyaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DincharyaBindModule {

    @Binds
    @Singleton
    abstract fun bindDincharyaRepository(impl: DincharyaRepositoryImpl): DincharyaRepository
}
