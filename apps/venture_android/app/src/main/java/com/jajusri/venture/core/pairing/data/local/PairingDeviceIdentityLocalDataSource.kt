package com.jajusri.venture.core.pairing.data.local

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jajusri.venture.core.pairing.domain.model.PairingDeviceIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Narrow seam over [Build.MODEL] so the identity data source stays unit-testable without Robolectric. */
interface DeviceLabelProvider {
    fun defaultDeviceLabel(): String
}

@Singleton
class DefaultDeviceLabelProvider @Inject constructor() : DeviceLabelProvider {
    override fun defaultDeviceLabel(): String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android Device"
}

interface PairingDeviceIdentityLocalDataSource {
    /** Returns the persisted identity, generating and persisting one on first call. */
    suspend fun getOrCreate(): PairingDeviceIdentity
}

private val Context.pairingDeviceIdentityDataStore: DataStore<Preferences> by preferencesDataStore(name = "pairing_device_identity")

@Singleton
class DataStorePairingDeviceIdentityLocalDataSource @Inject constructor(
    @ApplicationContext context: Context,
    private val deviceLabelProvider: DeviceLabelProvider,
) : PairingDeviceIdentityLocalDataSource {

    private val dataStore = context.pairingDeviceIdentityDataStore

    /** Guards against two concurrent first-calls generating and persisting two different UUIDs. */
    private val mutex = Mutex()

    override suspend fun getOrCreate(): PairingDeviceIdentity {
        readExisting()?.let { return it }
        return mutex.withLock {
            readExisting()?.let { return@withLock it }
            val generated = PairingDeviceIdentity(
                logicalDeviceId = UUID.randomUUID().toString(),
                deviceLabel = deviceLabelProvider.defaultDeviceLabel(),
            )
            dataStore.edit { prefs ->
                prefs[KEY_DEVICE_ID] = generated.logicalDeviceId
                prefs[KEY_DEVICE_LABEL] = generated.deviceLabel
            }
            generated
        }
    }

    private suspend fun readExisting(): PairingDeviceIdentity? = dataStore.data.map { prefs ->
        val id = prefs[KEY_DEVICE_ID]
        val label = prefs[KEY_DEVICE_LABEL]
        if (id != null && label != null) PairingDeviceIdentity(id, label) else null
    }.first()

    private companion object {
        val KEY_DEVICE_ID = stringPreferencesKey("logical_device_id")
        val KEY_DEVICE_LABEL = stringPreferencesKey("device_label")
    }
}
