package com.jajusri.venture.feature.businessprofile.presentation

import android.net.Uri
import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.businessprofile.domain.model.BusinessProfile
import com.jajusri.venture.feature.businessprofile.domain.model.BusinessProfileDraft
import com.jajusri.venture.feature.businessprofile.domain.repository.BusinessProfileRepository
import com.jajusri.venture.feature.businessprofile.domain.usecase.ClearBusinessProfileLogoUseCase
import com.jajusri.venture.feature.businessprofile.domain.usecase.GetBusinessProfileUseCase
import com.jajusri.venture.feature.businessprofile.domain.usecase.ResolveBusinessProfileLogoFileUseCase
import com.jajusri.venture.feature.businessprofile.domain.usecase.SaveBusinessProfileUseCase
import com.jajusri.venture.feature.businessprofile.domain.usecase.UpdateBusinessProfileLogoUseCase
import com.jajusri.venture.feature.businessprofile.storage.BusinessProfileLogoFailureReason
import com.jajusri.venture.feature.businessprofile.storage.BusinessProfileLogoResult
import com.jajusri.venture.feature.company.domain.port.CompanySessionPort
import com.jajusri.venture.feature.company.domain.port.SelectedCompanyStatus
import com.jajusri.venture.feature.company.domain.port.SessionValidationStatus
import com.jajusri.venture.feature.masterdata.presentation.MasterDataUiError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BusinessProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(repository: FakeBusinessProfileRepository, company: FakeCompanySession): BusinessProfileViewModel =
        BusinessProfileViewModel(
            GetBusinessProfileUseCase(repository),
            SaveBusinessProfileUseCase(repository),
            UpdateBusinessProfileLogoUseCase(repository),
            ClearBusinessProfileLogoUseCase(repository),
            ResolveBusinessProfileLogoFileUseCase(repository),
            company,
        )

    private fun profile(companyId: String, tradingName: String = "Acme Traders") = BusinessProfile(
        companyId = companyId,
        tradingName = tradingName,
        legalName = "Acme Traders Pvt Ltd",
        addressLine1 = null,
        addressCity = null,
        addressState = null,
        addressPincode = null,
        phone = null,
        phoneNormalized = null,
        email = null,
        gstin = null,
        website = null,
        description = null,
        logoAssetPath = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    @Test
    fun `no company selected shows a message error state`() = runTest(dispatcher) {
        val viewModel = createViewModel(FakeBusinessProfileRepository(), FakeCompanySession(null))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.error is MasterDataUiError.Message)
    }

    @Test
    fun `no saved profile shows the empty state, not an error`() = runTest(dispatcher) {
        val viewModel = createViewModel(FakeBusinessProfileRepository(), FakeCompanySession("co-a"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasSavedProfile)
        assertNull(state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `an existing profile loads into both the live form and the saved snapshot`() = runTest(dispatcher) {
        val repo = FakeBusinessProfileRepository()
        repo.profiles["co-a"] = profile("co-a")
        val viewModel = createViewModel(repo, FakeCompanySession("co-a"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasSavedProfile)
        assertEquals("Acme Traders", state.form.tradingName)
        assertEquals("Acme Traders", state.savedForm.tradingName)
    }

    @Test
    fun `saving without a trading name shows a notice and never calls the repository`() = runTest(dispatcher) {
        val repo = FakeBusinessProfileRepository()
        val viewModel = createViewModel(repo, FakeCompanySession("co-a"))
        advanceUntilIdle()
        viewModel.onEvent(BusinessProfileEvent.EditTapped)

        viewModel.onEvent(BusinessProfileEvent.SaveTapped)
        advanceUntilIdle()

        assertEquals("Business / Trading Name is required.", viewModel.uiState.value.notice)
        assertTrue(repo.savedDrafts.isEmpty())
    }

    @Test
    fun `saving with a trading name persists the profile and exits edit mode`() = runTest(dispatcher) {
        val repo = FakeBusinessProfileRepository()
        val viewModel = createViewModel(repo, FakeCompanySession("co-a"))
        advanceUntilIdle()
        viewModel.onEvent(BusinessProfileEvent.EditTapped)
        viewModel.onEvent(BusinessProfileEvent.TradingNameChanged("Acme Traders"))

        viewModel.onEvent(BusinessProfileEvent.SaveTapped)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isEditing)
        assertTrue(state.hasSavedProfile)
        assertEquals("Acme Traders", state.form.tradingName)
        assertEquals(1, repo.savedDrafts.size)
    }

    @Test
    fun `cancelling an edit reverts to the last-saved values, discarding unsaved changes`() = runTest(dispatcher) {
        val repo = FakeBusinessProfileRepository()
        repo.profiles["co-a"] = profile("co-a", tradingName = "Original Name")
        val viewModel = createViewModel(repo, FakeCompanySession("co-a"))
        advanceUntilIdle()

        viewModel.onEvent(BusinessProfileEvent.EditTapped)
        viewModel.onEvent(BusinessProfileEvent.TradingNameChanged("Half-typed change"))
        viewModel.onEvent(BusinessProfileEvent.CancelEditTapped)

        val state = viewModel.uiState.value
        assertFalse(state.isEditing)
        assertEquals("Original Name", state.form.tradingName)
    }

    @Test
    fun `switching companies loads a fresh profile and discards any unsaved edit, never mixing data`() = runTest(dispatcher) {
        val repo = FakeBusinessProfileRepository()
        repo.profiles["co-a"] = profile("co-a", tradingName = "Company A Business")
        repo.profiles["co-b"] = profile("co-b", tradingName = "Company B Business")
        val company = FakeCompanySession("co-a")
        val viewModel = createViewModel(repo, company)
        advanceUntilIdle()
        viewModel.onEvent(BusinessProfileEvent.EditTapped)
        viewModel.onEvent(BusinessProfileEvent.TradingNameChanged("Unsaved edit for A"))

        company.selected.value = "co-b"
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("co-b", state.companyId)
        assertEquals("Company B Business", state.form.tradingName)
        assertFalse(state.isEditing)
    }

    @Test
    fun `a save that completes after switching companies never overwrites the new companys state`() = runTest(dispatcher) {
        val repo = FakeBusinessProfileRepository()
        repo.profiles["co-a"] = profile("co-a", tradingName = "Company A Business")
        repo.profiles["co-b"] = profile("co-b", tradingName = "Company B Business")
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        val company = FakeCompanySession("co-a")
        val viewModel = createViewModel(repo, company)
        advanceUntilIdle()
        viewModel.onEvent(BusinessProfileEvent.EditTapped)
        viewModel.onEvent(BusinessProfileEvent.TradingNameChanged("Company A Renamed"))
        viewModel.onEvent(BusinessProfileEvent.SaveTapped)
        advanceUntilIdle() // save() is now suspended inside the fake repository, waiting on the gate

        company.selected.value = "co-b"
        advanceUntilIdle() // co-b's own load() is unaffected by the gate (only save/updateLogo await it) and completes fully now
        gate.complete(Unit) // only now let the stale co-a save resolve
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("co-b", state.companyId)
        assertEquals("Company B Business", state.form.tradingName)
        assertEquals("Company A Renamed", repo.profiles["co-a"]?.tradingName) // the write itself still succeeded on disk
    }

    @Test
    fun `a logo update that completes after switching companies never overwrites the new companys state`() = runTest(dispatcher) {
        val repo = FakeBusinessProfileRepository()
        repo.profiles["co-a"] = profile("co-a", tradingName = "Company A Business")
        repo.profiles["co-b"] = profile("co-b", tradingName = "Company B Business").copy(logoAssetPath = "/files/co-b.png")
        val bResolved = java.io.File("/files/co-b.png")
        repo.resolvedFileFor["/files/co-b.png"] = bResolved
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        val company = FakeCompanySession("co-a")
        val viewModel = createViewModel(repo, company)
        advanceUntilIdle()
        viewModel.onEvent(BusinessProfileEvent.LogoPicked(android.net.TestUri.create()))
        advanceUntilIdle() // updateLogo() is now suspended inside the fake repository, waiting on the gate

        company.selected.value = "co-b"
        gate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("co-b", state.companyId)
        assertEquals(bResolved, state.logoFile) // never clobbered by co-a's stale logo update result
    }

    @Test
    fun `a repository failure on save surfaces a notice without crashing`() = runTest(dispatcher) {
        val repo = FakeBusinessProfileRepository().apply { shouldThrowOnSave = true }
        val viewModel = createViewModel(repo, FakeCompanySession("co-a"))
        advanceUntilIdle()
        viewModel.onEvent(BusinessProfileEvent.EditTapped)
        viewModel.onEvent(BusinessProfileEvent.TradingNameChanged("Acme Traders"))

        viewModel.onEvent(BusinessProfileEvent.SaveTapped)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertTrue(state.notice != null)
    }

    // ============================== LOGO (MVP-1.3-B) ==============================

    @Test
    fun `change logo tapped requests the system picker via an effect, never touching state directly`() = runTest(dispatcher) {
        val repo = FakeBusinessProfileRepository()
        repo.profiles["co-a"] = profile("co-a")
        val viewModel = createViewModel(repo, FakeCompanySession("co-a"))
        advanceUntilIdle()
        val effects = mutableListOf<BusinessProfileEffect>()
        val job = launch { viewModel.effects.collect { effects.add(it) } }
        advanceUntilIdle()

        viewModel.onEvent(BusinessProfileEvent.ChangeLogoTapped)
        advanceUntilIdle()

        assertTrue(effects.contains(BusinessProfileEffect.RequestLogoPick))
        job.cancel()
    }

    @Test
    fun `picking a logo before any profile is saved shows a notice and never calls the repository`() = runTest(dispatcher) {
        val repo = FakeBusinessProfileRepository()
        val viewModel = createViewModel(repo, FakeCompanySession("co-a"))
        advanceUntilIdle()

        viewModel.onEvent(BusinessProfileEvent.LogoPicked(android.net.TestUri.create()))
        advanceUntilIdle()

        assertEquals("Save the Business Profile before adding a logo.", viewModel.uiState.value.notice)
    }

    @Test
    fun `picking a logo for an existing profile resolves and stores the returned file`() = runTest(dispatcher) {
        val repo = FakeBusinessProfileRepository()
        repo.profiles["co-a"] = profile("co-a")
        val resolved = java.io.File("/files/business_profile_logos/co-a.jpg")
        repo.resolvedFileFor["/files/business_profile_logos/co-a.jpg"] = resolved
        val viewModel = createViewModel(repo, FakeCompanySession("co-a"))
        advanceUntilIdle()

        viewModel.onEvent(BusinessProfileEvent.LogoPicked(android.net.TestUri.create()))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("/files/business_profile_logos/co-a.jpg", state.logoAssetPath)
        assertEquals(resolved, state.logoFile)
        assertEquals("Logo updated.", state.notice)
        assertFalse(state.isUpdatingLogo)
    }

    @Test
    fun `a rejected logo shows the specific reason and never touches the stored logo path`() = runTest(dispatcher) {
        val repo = FakeBusinessProfileRepository().apply {
            nextLogoResult = BusinessProfileLogoResult.Failure(BusinessProfileLogoFailureReason.FileTooLarge)
        }
        repo.profiles["co-a"] = profile("co-a")
        val viewModel = createViewModel(repo, FakeCompanySession("co-a"))
        advanceUntilIdle()

        viewModel.onEvent(BusinessProfileEvent.LogoPicked(android.net.TestUri.create()))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("That image is too large (max 5 MB).", state.notice)
        assertNull(state.logoAssetPath)
    }

    @Test
    fun `clearing the logo removes it from state and shows a confirmation notice`() = runTest(dispatcher) {
        val repo = FakeBusinessProfileRepository()
        val resolved = java.io.File("/files/business_profile_logos/co-a.jpg")
        repo.resolvedFileFor["/files/business_profile_logos/co-a.jpg"] = resolved
        repo.profiles["co-a"] = profile("co-a").copy(logoAssetPath = "/files/business_profile_logos/co-a.jpg")
        val viewModel = createViewModel(repo, FakeCompanySession("co-a"))
        advanceUntilIdle()
        assertEquals(resolved, viewModel.uiState.value.logoFile)

        viewModel.onEvent(BusinessProfileEvent.ClearLogoTapped)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.logoAssetPath)
        assertNull(state.logoFile)
        assertEquals("Logo removed.", state.notice)
    }
}

private class FakeBusinessProfileRepository : BusinessProfileRepository {
    val profiles = mutableMapOf<String, BusinessProfile>()
    val savedDrafts = mutableListOf<Pair<String, BusinessProfileDraft>>()
    var shouldThrowOnSave = false
    var nextLogoResult: BusinessProfileLogoResult = BusinessProfileLogoResult.Success("/files/business_profile_logos/co-a.jpg")
    var resolvedFileFor: MutableMap<String, java.io.File> = mutableMapOf()
    /** When non-null, [saveProfile]/[updateLogo] suspend here before returning -- lets a test
     * simulate "the write is still in flight" and interleave a company switch before completing it. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun getProfile(companyId: String): BusinessProfile? = profiles[companyId]

    override suspend fun saveProfile(companyId: String, draft: BusinessProfileDraft): BusinessProfile {
        gate?.await()
        if (shouldThrowOnSave) error("simulated repository failure")
        savedDrafts += companyId to draft
        val saved = BusinessProfile(
            companyId = companyId,
            tradingName = draft.tradingName,
            legalName = draft.legalName,
            addressLine1 = draft.addressLine1,
            addressCity = draft.addressCity,
            addressState = draft.addressState,
            addressPincode = draft.addressPincode,
            phone = draft.phone,
            phoneNormalized = draft.phone,
            email = draft.email,
            gstin = draft.gstin,
            website = draft.website,
            description = draft.description,
            logoAssetPath = profiles[companyId]?.logoAssetPath,
            createdAt = profiles[companyId]?.createdAt ?: 1_000L,
            updatedAt = 2_000L,
        )
        profiles[companyId] = saved
        return saved
    }

    override suspend fun updateLogo(companyId: String, sourceUri: Uri): BusinessProfileLogoResult? {
        gate?.await()
        val existing = profiles[companyId] ?: return null
        if (nextLogoResult is BusinessProfileLogoResult.Success) {
            val path = (nextLogoResult as BusinessProfileLogoResult.Success).logoAssetPath
            profiles[companyId] = existing.copy(logoAssetPath = path)
        }
        return nextLogoResult
    }

    override suspend fun clearLogo(companyId: String) {
        profiles[companyId]?.let { profiles[companyId] = it.copy(logoAssetPath = null) }
    }

    override suspend fun resolveLogoFile(logoAssetPath: String?): java.io.File? =
        logoAssetPath?.let { resolvedFileFor[it] }
}

private class FakeCompanySession(initial: String?) : CompanySessionPort {
    val selected = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = selected
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(selected.value, selected.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> =
        AppResult.Failure(AppError.Message("unused"))
}
