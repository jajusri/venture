package com.jajusri.venture.core.security

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindCredentialCipher(impl: AndroidKeystoreCredentialCipher): CredentialCipher

    @Binds
    @Singleton
    abstract fun bindSpkiFingerprintVerifier(impl: DefaultSpkiFingerprintVerifier): SpkiFingerprintVerifier
}
