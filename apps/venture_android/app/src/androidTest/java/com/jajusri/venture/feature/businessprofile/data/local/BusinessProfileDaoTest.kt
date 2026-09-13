package com.jajusri.venture.feature.businessprofile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jajusri.venture.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real-Room instrumented coverage for [BusinessProfileDao] (MVP-1.3-A, PDL-019). */
@RunWith(AndroidJUnit4::class)
class BusinessProfileDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: BusinessProfileDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.businessProfileDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun profile(
        companyId: String = "co-a",
        tradingName: String = "Acme Traders",
        legalName: String? = null,
        phone: String? = null,
        phoneNormalized: String? = null,
        gstin: String? = null,
        logoAssetPath: String? = null,
        createdAt: Long = 1_000L,
        updatedAt: Long = 1_000L,
    ) = BusinessProfileEntity(
        companyId = companyId,
        tradingName = tradingName,
        legalName = legalName,
        addressLine1 = null,
        addressCity = null,
        addressState = null,
        addressPincode = null,
        phone = phone,
        phoneNormalized = phoneNormalized,
        email = null,
        gstin = gstin,
        website = null,
        description = null,
        logoAssetPath = logoAssetPath,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun findByCompanyReturnsNullWhenNoProfileExists() = runBlocking {
        assertNull(dao.findByCompany("co-a"))
    }

    @Test
    fun upsertThenFindRoundTrips() = runBlocking {
        dao.upsert(profile(tradingName = "Acme Traders", legalName = "Acme Traders Pvt Ltd", gstin = "27ABCDE1234F1Z5"))

        val stored = dao.findByCompany("co-a")

        assertEquals("Acme Traders", stored?.tradingName)
        assertEquals("Acme Traders Pvt Ltd", stored?.legalName)
        assertEquals("27ABCDE1234F1Z5", stored?.gstin)
    }

    @Test
    fun upsertReplacesRatherThanDuplicatesForTheSameCompany() = runBlocking {
        dao.upsert(profile(tradingName = "Acme Traders"))
        dao.upsert(profile(tradingName = "Acme Traders Renamed"))

        assertEquals("Acme Traders Renamed", dao.findByCompany("co-a")?.tradingName)
    }

    @Test
    fun updatingOnlyTheLogoPreservesEveryOtherField() = runBlocking {
        dao.upsert(profile(tradingName = "Acme Traders", gstin = "27ABCDE1234F1Z5"))
        val existing = requireNotNull(dao.findByCompany("co-a"))

        dao.upsert(existing.copy(logoAssetPath = "/data/user/0/com.jajusri.venture.debug/files/business_profile_logos/co-a.png"))

        val updated = dao.findByCompany("co-a")
        assertEquals("Acme Traders", updated?.tradingName)
        assertEquals("27ABCDE1234F1Z5", updated?.gstin)
        assertEquals("/data/user/0/com.jajusri.venture.debug/files/business_profile_logos/co-a.png", updated?.logoAssetPath)
    }

    // ============================== COMPANY ISOLATION (PDL-019) ==============================

    @Test
    fun profilesAreCompanyIsolatedEvenWithIdenticalTradingNamesPhonesAndGstin() = runBlocking {
        dao.upsert(profile(companyId = "co-a", tradingName = "Alpha", phone = "9876543210", phoneNormalized = "9876543210", gstin = "27ABCDE1234F1Z5"))
        dao.upsert(profile(companyId = "co-b", tradingName = "Alpha", phone = "9876543210", phoneNormalized = "9876543210", gstin = "27ABCDE1234F1Z5"))

        val profileA = dao.findByCompany("co-a")
        val profileB = dao.findByCompany("co-b")

        assertEquals("co-a", profileA?.companyId)
        assertEquals("co-b", profileB?.companyId)

        // Prove they are genuinely independent rows, not one shared row: changing A must never
        // affect B, even though every displayed value started out identical.
        dao.upsert(requireNotNull(profileA).copy(tradingName = "Alpha (Renamed)"))
        assertEquals("Alpha (Renamed)", dao.findByCompany("co-a")?.tradingName)
        assertEquals("Alpha", dao.findByCompany("co-b")?.tradingName)
    }

    @Test
    fun aProfileMissingForOneCompanyDoesNotLeakAnotherCompanysProfile() = runBlocking {
        dao.upsert(profile(companyId = "co-a", tradingName = "Alpha"))

        assertEquals("Alpha", dao.findByCompany("co-a")?.tradingName)
        assertNull("co-b has no profile of its own and must never see co-a's", dao.findByCompany("co-b"))
    }

    @Test
    fun logoBelongingToOneCompanyNeverAppearsForAnother() = runBlocking {
        dao.upsert(profile(companyId = "co-a", tradingName = "Alpha", logoAssetPath = "/files/business_profile_logos/co-a.png"))
        dao.upsert(profile(companyId = "co-b", tradingName = "Beta", logoAssetPath = null))

        assertEquals("/files/business_profile_logos/co-a.png", dao.findByCompany("co-a")?.logoAssetPath)
        assertNull(dao.findByCompany("co-b")?.logoAssetPath)
    }
}
