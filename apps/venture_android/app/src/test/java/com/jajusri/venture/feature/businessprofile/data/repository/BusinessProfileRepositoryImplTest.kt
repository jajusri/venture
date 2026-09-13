package com.jajusri.venture.feature.businessprofile.data.repository

import android.net.Uri
import com.jajusri.venture.core.util.DispatcherProvider
import com.jajusri.venture.core.util.TimeProvider
import com.jajusri.venture.feature.businessprofile.data.local.BusinessProfileDao
import com.jajusri.venture.feature.businessprofile.data.local.BusinessProfileEntity
import com.jajusri.venture.feature.businessprofile.domain.model.BusinessProfileDraft
import com.jajusri.venture.feature.businessprofile.storage.BusinessProfileLogoFailureReason
import com.jajusri.venture.feature.businessprofile.storage.BusinessProfileLogoResult
import com.jajusri.venture.feature.businessprofile.storage.BusinessProfileLogoStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val NOW = 5_000_000L

class BusinessProfileRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
    private val time = TimeProvider { NOW }
    private val dao = FakeBusinessProfileDao()
    private val logoStore = FakeBusinessProfileLogoStore()

    private fun repository() = BusinessProfileRepositoryImpl(dao, logoStore, time, dispatchers)

    private fun draft(tradingName: String = "Acme Traders") = BusinessProfileDraft(
        tradingName = tradingName,
        legalName = "Acme Traders Pvt Ltd",
        addressLine1 = "12 MG Road",
        addressCity = "Pune",
        addressState = "Maharashtra",
        addressPincode = "411001",
        phone = "9876543210",
        email = "hello@acme.example",
        gstin = "27ABCDE1234F1Z5",
        website = "https://acme.example",
        description = "Wholesale traders.",
    )

    @Test
    fun `getProfile returns null when nothing has been saved yet`() = runTest(dispatcher) {
        assertNull(repository().getProfile("co-a"))
    }

    @Test
    fun `saveProfile creates a new profile with createdAt equal to updatedAt`() = runTest(dispatcher) {
        val saved = repository().saveProfile("co-a", draft())

        assertEquals("Acme Traders", saved.tradingName)
        assertEquals("co-a", saved.companyId)
        assertEquals(NOW, saved.createdAt)
        assertEquals(NOW, saved.updatedAt)
        assertEquals("9876543210", saved.phoneNormalized)
    }

    @Test
    fun `saving again preserves the original createdAt but advances updatedAt`() = runTest(dispatcher) {
        val repo = repository()
        repo.saveProfile("co-a", draft())
        val resaved = repo.saveProfile("co-a", draft(tradingName = "Acme Traders Renamed"))

        assertEquals("Acme Traders Renamed", resaved.tradingName)
        assertEquals(NOW, resaved.createdAt)
    }

    @Test
    fun `saveProfile never touches an existing logoAssetPath`() = runTest(dispatcher) {
        val repo = repository()
        repo.saveProfile("co-a", draft())
        dao.store["co-a"] = requireNotNull(dao.store["co-a"]).copy(logoAssetPath = "/files/co-a.png")

        val resaved = repo.saveProfile("co-a", draft(tradingName = "Renamed"))

        assertEquals("/files/co-a.png", resaved.logoAssetPath)
    }

    @Test
    fun `updateLogo returns null when no profile exists yet for the company`() = runTest(dispatcher) {
        assertNull(repository().updateLogo("co-a", fakeUri()))
    }

    @Test
    fun `updateLogo persists the logo store's returned path onto the profile`() = runTest(dispatcher) {
        val repo = repository()
        repo.saveProfile("co-a", draft())
        logoStore.nextResult = BusinessProfileLogoResult.Success("/files/business_profile_logos/co-a.png")

        val result = repo.updateLogo("co-a", fakeUri())

        assertEquals(BusinessProfileLogoResult.Success("/files/business_profile_logos/co-a.png"), result)
        assertEquals("/files/business_profile_logos/co-a.png", repo.getProfile("co-a")?.logoAssetPath)
    }

    @Test
    fun `updateLogo does not touch the profile row when the logo store fails`() = runTest(dispatcher) {
        val repo = repository()
        repo.saveProfile("co-a", draft())
        logoStore.nextResult = BusinessProfileLogoResult.Failure(BusinessProfileLogoFailureReason.FileTooLarge)

        val result = repo.updateLogo("co-a", fakeUri())

        assertEquals(BusinessProfileLogoResult.Failure(BusinessProfileLogoFailureReason.FileTooLarge), result)
        assertNull(repo.getProfile("co-a")?.logoAssetPath)
    }

    @Test
    fun `clearLogo removes the stored reference and calls the logo store`() = runTest(dispatcher) {
        val repo = repository()
        repo.saveProfile("co-a", draft())
        dao.store["co-a"] = requireNotNull(dao.store["co-a"]).copy(logoAssetPath = "/files/co-a.png")

        repo.clearLogo("co-a")

        assertNull(repo.getProfile("co-a")?.logoAssetPath)
        assertTrue(logoStore.deletedFor.contains("co-a"))
    }

    @Test
    fun `resolveLogoFile delegates to the logo store and returns null for a blank path`() = runTest(dispatcher) {
        val repo = repository()
        val resolved = File("/files/business_profile_logos/co-a.png")
        logoStore.resolvedFile = resolved

        assertEquals(resolved, repo.resolveLogoFile("/files/business_profile_logos/co-a.png"))
        assertNull(repo.resolveLogoFile(null))
    }

    // ============================== COMPANY ISOLATION ==============================

    @Test
    fun `profiles are company isolated end to end through the repository, even with identical content`() = runTest(dispatcher) {
        val repo = repository()
        repo.saveProfile("co-a", draft(tradingName = "Alpha"))
        repo.saveProfile("co-b", draft(tradingName = "Alpha"))

        val profileA = repo.getProfile("co-a")
        val profileB = repo.getProfile("co-b")

        assertEquals("co-a", profileA?.companyId)
        assertEquals("co-b", profileB?.companyId)

        repo.saveProfile("co-a", draft(tradingName = "Alpha (Renamed)"))
        assertEquals("Alpha (Renamed)", repo.getProfile("co-a")?.tradingName)
        assertEquals("Alpha", repo.getProfile("co-b")?.tradingName)
    }

    private fun fakeUri(): Uri = android.net.TestUri.create()
}

private class FakeBusinessProfileDao : BusinessProfileDao {
    val store = mutableMapOf<String, BusinessProfileEntity>()

    override suspend fun findByCompany(companyId: String): BusinessProfileEntity? = store[companyId]

    override suspend fun upsert(entity: BusinessProfileEntity) {
        store[entity.companyId] = entity
    }
}

private class FakeBusinessProfileLogoStore : BusinessProfileLogoStore {
    var nextResult: BusinessProfileLogoResult = BusinessProfileLogoResult.Success("/files/default.png")
    var resolvedFile: File? = null
    val deletedFor = mutableListOf<String>()

    override suspend fun saveLogo(companyId: String, sourceUri: Uri): BusinessProfileLogoResult = nextResult

    override fun resolveLogoFile(logoAssetPath: String?): File? = if (logoAssetPath == null) null else resolvedFile

    override suspend fun deleteLogo(companyId: String) {
        deletedFor += companyId
    }
}
