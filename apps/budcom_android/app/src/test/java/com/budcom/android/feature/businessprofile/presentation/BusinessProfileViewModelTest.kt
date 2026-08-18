package com.budcom.android.feature.businessprofile.presentation

import android.net.Uri
import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.businessprofile.domain.model.BusinessProfile
import com.budcom.android.feature.businessprofile.domain.model.BusinessProfileDraft
import com.budcom.android.feature.businessprofile.domain.repository.BusinessProfileRepository
import com.budcom.android.feature.businessprofile.domain.usecase.GetBusinessProfileUseCase
import com.budcom.android.feature.businessprofile.domain.usecase.SaveBusinessProfileUseCase
import com.budcom.android.feature.businessprofile.storage.BusinessProfileLogoResult
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        BusinessProfileViewModel(GetBusinessProfileUseCase(repository), SaveBusinessProfileUseCase(repository), company)

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
}

private class FakeBusinessProfileRepository : BusinessProfileRepository {
    val profiles = mutableMapOf<String, BusinessProfile>()
    val savedDrafts = mutableListOf<Pair<String, BusinessProfileDraft>>()
    var shouldThrowOnSave = false

    override suspend fun getProfile(companyId: String): BusinessProfile? = profiles[companyId]

    override suspend fun saveProfile(companyId: String, draft: BusinessProfileDraft): BusinessProfile {
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

    override suspend fun updateLogo(companyId: String, sourceUri: Uri): BusinessProfileLogoResult? = error("unused")

    override suspend fun clearLogo(companyId: String) = error("unused")
}

private class FakeCompanySession(initial: String?) : CompanySessionPort {
    val selected = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = selected
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(selected.value, selected.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> =
        AppResult.Failure(AppError.Message("unused"))
}
