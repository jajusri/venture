package com.budcom.android.navigation

import com.budcom.android.core.connection.ConnectorEnrolmentGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppRootViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `routes to Connector discovery when the enrolment gate says enrolment is needed`() = runTest(dispatcher) {
        val viewModel = AppRootViewModel(enrolmentGate = FakeGate(needsEnrolment = true))

        advanceUntilIdle()

        assertEquals(Routes.CONNECTOR_DISCOVERY, viewModel.startDestination.value)
    }

    @Test
    fun `routes straight to Dashboard when the enrolment gate says no enrolment is needed`() = runTest(dispatcher) {
        val viewModel = AppRootViewModel(enrolmentGate = FakeGate(needsEnrolment = false))

        advanceUntilIdle()

        assertEquals(Routes.HOME, viewModel.startDestination.value)
    }

    @Test
    fun `start destination is null until the gate check resolves, never defaulting to Dashboard early`() = runTest(dispatcher) {
        val viewModel = AppRootViewModel(enrolmentGate = FakeGate(needsEnrolment = true))

        assertEquals(null, viewModel.startDestination.value)

        advanceUntilIdle()
        assertEquals(Routes.CONNECTOR_DISCOVERY, viewModel.startDestination.value)
    }
}

private class FakeGate(private val needsEnrolment: Boolean) : ConnectorEnrolmentGate {
    override suspend fun needsEnrolment(): Boolean = needsEnrolment
}
