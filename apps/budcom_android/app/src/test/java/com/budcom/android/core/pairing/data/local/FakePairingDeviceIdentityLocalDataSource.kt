package com.budcom.android.core.pairing.data.local

import com.budcom.android.core.pairing.domain.model.PairingDeviceIdentity

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
