package com.jajusri.venture.core.connection.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Connector this device has successfully paired with, keyed by its stable identity
 * (not its network address, which can change on DHCP renewal/Wi-Fi change/router restart).
 */
@Entity(tableName = "paired_connectors")
data class PairedConnectorEntity(
    @PrimaryKey val connectorId: String,
    val friendlyName: String,
    val lastKnownHost: String,
    val lastKnownPort: Int,
    val lastConnectedAtEpochMillis: Long,
    val createdAtEpochMillis: Long,
)
