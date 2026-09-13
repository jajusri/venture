package com.jajusri.venture.feature.transaction.data.di

import com.jajusri.venture.core.security.AndroidVartalapDeviceKeyStore
import com.jajusri.venture.feature.transaction.data.TransactionClockImpl
import com.jajusri.venture.feature.transaction.data.port.LocalTransactionSubmissionPort
import com.jajusri.venture.feature.transaction.data.relay.HttpRelayClient
import com.jajusri.venture.feature.transaction.data.relay.HttpRelayStructuredTransport
import com.jajusri.venture.feature.transaction.data.relay.DefaultRelayOutboxDispatcher
import com.jajusri.venture.feature.transaction.data.relay.DefaultRelayRecipientInboxIngester
import com.jajusri.venture.feature.transaction.data.repository.StructuredRecipientInboxRepositoryImpl
import com.jajusri.venture.feature.transaction.domain.port.RelayRecipientInboxIngester
import com.jajusri.venture.feature.transaction.domain.repository.StructuredRecipientInboxRepository
import com.jajusri.venture.feature.transaction.data.relay.KeystoreRelayEnvelopeAuthenticator
import com.jajusri.venture.feature.transaction.domain.port.OrderSentFromRelayEvidence
import com.jajusri.venture.feature.transaction.domain.port.RelayOutboxDispatcher
import com.jajusri.venture.feature.transaction.data.repository.TransactionRepositoryImpl
import com.jajusri.venture.feature.transaction.domain.model.TransactionClock
import com.jajusri.venture.core.security.AuthorityEpochCache
import com.jajusri.venture.core.security.CachedTransportCredentialVerifier
import com.jajusri.venture.core.security.IssuerVerificationKeyCache
import com.jajusri.venture.feature.transaction.domain.model.CommercialActionAuthorityResolver
import com.jajusri.venture.feature.transaction.domain.model.CommercialTrustCredentialSource
import com.jajusri.venture.feature.transaction.domain.model.TrustVerifiedCommercialActionAuthorityResolver
import com.jajusri.venture.feature.transaction.domain.port.TransportCredentialVerifier
import com.jajusri.venture.feature.transaction.domain.port.RelayEndpointProvider
import com.jajusri.venture.feature.transaction.domain.port.RelayEnvelopeAuthenticator
import com.jajusri.venture.feature.transaction.domain.port.StructuredBusinessTransport
import com.jajusri.venture.feature.transaction.domain.port.NoOpTransactionReminderScheduler
import com.jajusri.venture.feature.transaction.domain.port.TransactionReminderScheduler
import com.jajusri.venture.feature.transaction.domain.port.TransactionSubmissionPort
import com.jajusri.venture.feature.transaction.domain.port.VartalapDeviceKeyStore
import com.jajusri.venture.feature.transaction.domain.repository.TransactionRepository
import com.jajusri.venture.feature.transaction.domain.model.AuthenticatedCounterpartyBindingRepository
import com.jajusri.venture.feature.transaction.data.repository.AuthenticatedCounterpartyBindingRepositoryImpl
import com.jajusri.venture.feature.transaction.sharing.AndroidTransactionShareCoordinator
import com.jajusri.venture.feature.transaction.sharing.TransactionShareCoordinator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * [bindTransactionSubmissionPort] and [bindTransactionReminderScheduler] are the two bindings that
 * matter most here: swapping either to a real implementation later (a genuine cross-company
 * transport, a real WorkManager-based scheduler) is a one-line change to this module, with zero
 * change anywhere in [TransactionRepositoryImpl] or the domain layer — that is the entire point of
 * routing both through ports (architecture Findings 1/2).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TransactionBindModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindAuthenticatedCounterpartyBindingRepository(
        impl: AuthenticatedCounterpartyBindingRepositoryImpl,
    ): AuthenticatedCounterpartyBindingRepository

    @Binds
    @Singleton
    abstract fun bindTransactionClock(impl: TransactionClockImpl): TransactionClock

    @Binds
    @Singleton
    abstract fun bindTransactionSubmissionPort(impl: LocalTransactionSubmissionPort): TransactionSubmissionPort

    @Binds
    @Singleton
    abstract fun bindTransactionReminderScheduler(impl: NoOpTransactionReminderScheduler): TransactionReminderScheduler

    @Binds
    @Singleton
    abstract fun bindTransactionShareCoordinator(impl: AndroidTransactionShareCoordinator): TransactionShareCoordinator

    @Binds
    @Singleton
    abstract fun bindVartalapDeviceKeyStore(impl: AndroidVartalapDeviceKeyStore): VartalapDeviceKeyStore

    @Binds
    @Singleton
    abstract fun bindRelayEnvelopeAuthenticator(impl: KeystoreRelayEnvelopeAuthenticator): RelayEnvelopeAuthenticator

    @Binds
    @Singleton
    abstract fun bindRelayOutboxDispatcher(impl: DefaultRelayOutboxDispatcher): RelayOutboxDispatcher

    @Binds
    @Singleton
    abstract fun bindOrderSentFromRelayEvidence(impl: TransactionRepositoryImpl): OrderSentFromRelayEvidence

    @Binds
    @Singleton
    abstract fun bindStructuredRecipientInboxRepository(impl: StructuredRecipientInboxRepositoryImpl): StructuredRecipientInboxRepository

    @Binds
    @Singleton
    abstract fun bindRelayRecipientInboxIngester(impl: DefaultRelayRecipientInboxIngester): RelayRecipientInboxIngester

    @Binds
    @Singleton
    abstract fun bindStructuredBusinessTransport(impl: HttpRelayStructuredTransport): StructuredBusinessTransport

    @Binds
    @Singleton
    abstract fun bindCommercialDbTransaction(impl: com.jajusri.venture.feature.transaction.data.local.RoomCommercialDbTransaction): com.jajusri.venture.feature.transaction.data.local.CommercialDbTransaction
}

@Module
@InstallIn(SingletonComponent::class)
object RelayTransportModule {
    // RelayEndpointProvider, RelayCredentialSource, CommercialTrustCredentialSource,
    // IssuerVerificationKeyCache, and AuthorityEpochCache are no longer provided here as inert
    // stubs (Gate 5A/5B, architecturally approved DI-wiring edit): each is now bound to a real,
    // Trust-backed implementation from `core/trust/di/TrustModule.kt` / `core/relay/di/RelayConfigModule.kt`
    // (see the final report). This module's own commercial/domain wiring below
    // (`TransportCredentialVerifier`, `CommercialActionAuthorityResolver`) is UNCHANGED -- only
    // where their dependencies come from changed, not what they do with them.
    @Provides
    @Singleton
    fun provideHttpRelayClient(endpoint: RelayEndpointProvider): HttpRelayClient = HttpRelayClient(endpoint)

    @Provides
    @Singleton
    fun provideTransportCredentialVerifier(
        keys: IssuerVerificationKeyCache,
        epochs: AuthorityEpochCache,
    ): TransportCredentialVerifier = CachedTransportCredentialVerifier(keys, epochs)

    @Provides
    @Singleton
    fun provideCommercialActionAuthorityResolver(
        credentials: CommercialTrustCredentialSource,
        verifier: TransportCredentialVerifier,
        keyStore: VartalapDeviceKeyStore,
        epochs: AuthorityEpochCache,
    ): CommercialActionAuthorityResolver = TrustVerifiedCommercialActionAuthorityResolver(
        credentials,
        verifier,
        keyStore,
        epochs,
    )
}
