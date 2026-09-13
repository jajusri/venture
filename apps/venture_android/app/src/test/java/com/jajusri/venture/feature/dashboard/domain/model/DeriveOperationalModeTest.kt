package com.jajusri.venture.feature.dashboard.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DeriveOperationalModeTest {

    @Test
    fun offline() {
        assertEquals(
            DashboardOperationalMode.Offline,
            deriveOperationalMode(base().copy(isOnline = false)),
        )
    }

    @Test
    fun noServerConfiguration() {
        assertEquals(
            DashboardOperationalMode.NoServerConfiguration,
            deriveOperationalMode(base().copy(baseUrl = "")),
        )
    }

    @Test
    fun connectorUnavailable() {
        assertEquals(
            DashboardOperationalMode.ConnectorUnavailable,
            deriveOperationalMode(
                base().copy(healthPresent = false, connectorErrorPresent = true),
            ),
        )
    }

    @Test
    fun notReady() {
        assertEquals(
            DashboardOperationalMode.NotReady,
            deriveOperationalMode(
                base().copy(healthPresent = true, readinessStatus = "not_ready"),
            ),
        )
    }

    @Test
    fun noCompanySelectedWhenReady() {
        assertEquals(
            DashboardOperationalMode.NoCompanySelected,
            deriveOperationalMode(
                base().copy(
                    healthPresent = true,
                    readinessStatus = "ready",
                    selectedCompanyId = null,
                ),
            ),
        )
    }

    @Test
    fun sessionInvalid() {
        assertEquals(
            DashboardOperationalMode.SessionInvalid,
            deriveOperationalMode(
                base().copy(
                    healthPresent = true,
                    readinessStatus = "ready",
                    selectedCompanyId = "c1",
                    sessionValidity = DashboardSessionValidity.Invalid,
                ),
            ),
        )
    }

    @Test
    fun fullyOperational() {
        assertEquals(
            DashboardOperationalMode.FullyOperational,
            deriveOperationalMode(
                base().copy(
                    healthPresent = true,
                    readinessStatus = "ready",
                    selectedCompanyId = "c1",
                    sessionValidity = DashboardSessionValidity.Valid,
                ),
            ),
        )
    }

    @Test
    fun partialWhenHealthUnknownAndCompanySelected() {
        assertEquals(
            DashboardOperationalMode.PartiallyAvailable,
            deriveOperationalMode(
                base().copy(
                    healthPresent = false,
                    connectorErrorPresent = false,
                    selectedCompanyId = "c1",
                    sessionValidity = DashboardSessionValidity.Unknown,
                ),
            ),
        )
    }

    private fun base() = DashboardOperationalInputs(
        isOnline = true,
        baseUrl = "http://10.0.2.2:8080/",
        healthPresent = false,
        connectorErrorPresent = false,
        readinessStatus = null,
        selectedCompanyId = null,
        sessionValidity = DashboardSessionValidity.NoCompany,
    )
}
