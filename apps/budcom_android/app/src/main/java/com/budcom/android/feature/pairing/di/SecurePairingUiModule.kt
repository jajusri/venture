package com.budcom.android.feature.pairing.di

import com.budcom.android.feature.pairing.data.scanner.SecurePairingScannerPort
import com.budcom.android.feature.pairing.data.scanner.ZxingSecurePairingScannerAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurePairingUiBindModule {

    @Binds
    @Singleton
    abstract fun bindSecurePairingScannerPort(impl: ZxingSecurePairingScannerAdapter): SecurePairingScannerPort
}
