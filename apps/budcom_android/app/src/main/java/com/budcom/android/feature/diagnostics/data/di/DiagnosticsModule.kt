package com.budcom.android.feature.diagnostics.data.di

import com.budcom.android.feature.diagnostics.data.remote.DiagnosticsApi
import com.budcom.android.feature.diagnostics.data.repository.ConnectionDiagnosticsPortImpl
import com.budcom.android.feature.diagnostics.domain.port.ConnectionDiagnosticsPort
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsBindModule {
    @Binds
    @Singleton
    abstract fun bindConnectionDiagnosticsPort(
        impl: ConnectionDiagnosticsPortImpl,
    ): ConnectionDiagnosticsPort
}

@Module
@InstallIn(SingletonComponent::class)
object DiagnosticsProvideModule {
    @Provides
    @Singleton
    fun provideDiagnosticsApi(retrofit: Retrofit): DiagnosticsApi =
        retrofit.create(DiagnosticsApi::class.java)
}
