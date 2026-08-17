package com.budcom.android.feature.connect.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.budcom.android.ui.theme.BudcomTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProspectCreateScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun saveIsDisabledWithoutADisplayName() {
        composeRule.setContent {
            BudcomTheme { ProspectCreateScreen(state = ProspectCreateUiState(), onEvent = {}, onBack = {}) }
        }
        composeRule.onNodeWithTag("prospect_save").assertIsNotEnabled()
    }

    @Test
    fun saveIsEnabledOnceANameIsPresent() {
        composeRule.setContent {
            BudcomTheme { ProspectCreateScreen(state = ProspectCreateUiState(displayName = "New Bakery"), onEvent = {}, onBack = {}) }
        }
        composeRule.onNodeWithTag("prospect_save").assertIsEnabled()
    }

    @Test
    fun typingTheNameFieldEmitsDisplayNameChanged() {
        var lastEvent: ProspectCreateEvent? = null
        composeRule.setContent {
            BudcomTheme { ProspectCreateScreen(state = ProspectCreateUiState(), onEvent = { lastEvent = it }, onBack = {}) }
        }
        composeRule.onNodeWithTag("prospect_name").performTextInput("New Bakery")
        assertEquals(ProspectCreateEvent.DisplayNameChanged("New Bakery"), lastEvent)
    }

    @Test
    fun everyOptionalFieldIsPresentAndOptional() {
        composeRule.setContent {
            BudcomTheme { ProspectCreateScreen(state = ProspectCreateUiState(displayName = "New Bakery"), onEvent = {}, onBack = {}) }
        }
        composeRule.onNodeWithTag("prospect_phone").assertIsDisplayed()
        composeRule.onNodeWithTag("prospect_email").assertIsDisplayed()
        composeRule.onNodeWithTag("prospect_address").assertIsDisplayed()
        composeRule.onNodeWithTag("prospect_city").assertIsDisplayed()
        composeRule.onNodeWithTag("prospect_state").assertIsDisplayed()
        composeRule.onNodeWithTag("prospect_pincode").assertIsDisplayed()
        composeRule.onNodeWithTag("prospect_note").assertIsDisplayed()
        composeRule.onNodeWithTag("prospect_save").assertIsEnabled()
    }

    @Test
    fun tappingSaveEmitsSave() {
        var lastEvent: ProspectCreateEvent? = null
        composeRule.setContent {
            BudcomTheme { ProspectCreateScreen(state = ProspectCreateUiState(displayName = "New Bakery"), onEvent = { lastEvent = it }, onBack = {}) }
        }
        composeRule.onNodeWithTag("prospect_save").performClick()
        assertEquals(ProspectCreateEvent.Save, lastEvent)
    }

    @Test
    fun savingShowsAProgressLabel() {
        composeRule.setContent {
            BudcomTheme { ProspectCreateScreen(state = ProspectCreateUiState(displayName = "New Bakery", isSaving = true), onEvent = {}, onBack = {}) }
        }
        composeRule.onNodeWithTag("prospect_save").assertIsNotEnabled()
    }

    @Test
    fun tappingBackInvokesOnBack() {
        var backCalled = false
        composeRule.setContent {
            BudcomTheme { ProspectCreateScreen(state = ProspectCreateUiState(), onEvent = {}, onBack = { backCalled = true }) }
        }
        composeRule.onNodeWithTag("prospect_create_back").performClick()
        assertTrue(backCalled)
    }
}
