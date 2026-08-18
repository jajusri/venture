package com.budcom.android.feature.businessprofile.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * MVP-1.3-B "sharing foundation" (PDL-019) — pure-mapping coverage for
 * [BusinessProfile.toShareSnapshot]. No Android/Intent/network code is exercised here because none
 * exists yet: this is deliberately only the internal data-boundary, not an actual share feature.
 */
class BusinessProfileShareSnapshotTest {

    private fun profile(
        addressLine1: String? = null,
        addressCity: String? = null,
        addressState: String? = null,
        addressPincode: String? = null,
    ) = BusinessProfile(
        companyId = "co-a",
        tradingName = "Acme Traders",
        legalName = "Acme Traders Pvt Ltd",
        addressLine1 = addressLine1,
        addressCity = addressCity,
        addressState = addressState,
        addressPincode = addressPincode,
        phone = "9876543210",
        phoneNormalized = "9876543210",
        email = "hello@acme.example",
        gstin = "27ABCDE1234F1Z5",
        website = "https://acme.example",
        description = "Wholesale traders.",
        logoAssetPath = "/files/business_profile_logos/co-a.png",
        createdAt = 1_000L,
        updatedAt = 2_000L,
    )

    @Test
    fun `carries every share-appropriate field and never leaks companyId, timestamps, or the raw logo path`() {
        val snapshot = profile(
            addressLine1 = "12 MG Road",
            addressCity = "Pune",
            addressState = "Maharashtra",
            addressPincode = "411001",
        ).toShareSnapshot()

        assertEquals("Acme Traders", snapshot.tradingName)
        assertEquals("Acme Traders Pvt Ltd", snapshot.legalName)
        assertEquals("12 MG Road, Pune, Maharashtra, 411001", snapshot.formattedAddress)
        assertEquals("9876543210", snapshot.phone)
        assertEquals("hello@acme.example", snapshot.email)
        assertEquals("27ABCDE1234F1Z5", snapshot.gstin)
        assertEquals("https://acme.example", snapshot.website)
        assertEquals("Wholesale traders.", snapshot.description)
        // BusinessProfileShareSnapshot has no companyId/createdAt/updatedAt/logoAssetPath field at
        // all -- the type itself enforces this, not just this assertion.
    }

    @Test
    fun `a fully blank address produces a null formattedAddress, never an empty string of commas`() {
        val snapshot = profile().toShareSnapshot()

        assertNull(snapshot.formattedAddress)
    }

    @Test
    fun `a partial address omits only the missing components, with no dangling separators`() {
        val snapshot = profile(addressCity = "Pune", addressPincode = "411001").toShareSnapshot()

        assertEquals("Pune, 411001", snapshot.formattedAddress)
    }
}
