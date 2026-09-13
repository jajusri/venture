package com.jajusri.venture.core.pairing.di

import com.jajusri.venture.core.pairing.data.local.DataStorePairingDeviceIdentityLocalDataSource
import com.jajusri.venture.core.pairing.data.local.DataStoreSecureCredentialVault
import com.jajusri.venture.core.pairing.data.local.DefaultDeviceLabelProvider
import com.jajusri.venture.core.pairing.data.local.DeviceLabelProvider
import com.jajusri.venture.core.pairing.data.local.PairingDeviceIdentityLocalDataSource
import com.jajusri.venture.core.pairing.data.local.SecureCredentialVault
import com.jajusri.venture.core.pairing.data.remote.OkHttpPinnedHttpClientFactory
import com.jajusri.venture.core.pairing.data.remote.OkHttpSecurePairingApiClient
import com.jajusri.venture.core.pairing.data.remote.PinnedHttpClientFactory
import com.jajusri.venture.core.pairing.data.remote.SecurePairingApiPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindings for the secure-pairing client foundation (QR/short-code redemption, Keystore-backed
 * credential storage, pinned-HTTPS networking). Nothing here is consumed by any route, screen, or
 * navigation destination yet — see the Phase 3N evidence report for the deferred pairing-UI/QR-
 * scanner work this foundation exists to support.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SecurePairingBindModule {

    @Binds
    @Singleton
    abstract fun bindPairingDeviceIdentityLocalDataSource(
        impl: DataStorePairingDeviceIdentityLocalDataSource,
    ): PairingDeviceIdentityLocalDataSource

    @Binds
    @Singleton
    abstract fun bindDeviceLabelProvider(impl: DefaultDeviceLabelProvider): DeviceLabelProvider

    @Binds
    @Singleton
    abstract fun bindSecureCredentialVault(impl: DataStoreSecureCredentialVault): SecureCredentialVault

    @Binds
    @Singleton
    abstract fun bindPinnedHttpClientFactory(impl: OkHttpPinnedHttpClientFactory): PinnedHttpClientFactory

    @Binds
    @Singleton
    abstract fun bindSecurePairingApiPort(impl: OkHttpSecurePairingApiClient): SecurePairingApiPort
}
