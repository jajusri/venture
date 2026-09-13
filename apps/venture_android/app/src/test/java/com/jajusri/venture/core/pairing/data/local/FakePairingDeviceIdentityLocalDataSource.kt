package com.jajusri.venture.core.pairing.data.local

import com.jajusri.venture.core.pairing.domain.model.PairingDeviceIdentity

class FakePairingDeviceIdentityLocalDataSource(
    private val identity: PairingDeviceIdentity = PairingDeviceIdentity("device-uuid-fixed", "Test Phone"),
) : PairingDeviceIdentityLocalDataSource {
    var callCount = 0
        private set

    override suspend fun getOrCreate(): PairingDeviceIdentity {
        callCount++
        return identity
    }
}
