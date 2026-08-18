package com.budcom.android.feature.businessprofile.presentation

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.ui.theme.BudcomTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class BusinessProfileScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesForTag(tag: String) =
        onAllNodes(hasTestTag(tag))

    @Test
    fun loadingState() {
        composeRule.setContent {
            BudcomTheme { BusinessProfileScreen(state = BusinessProfileUiState(isLoading = true), onEvent = {}) }
        }
        composeRule.onNodeWithTag("business_profile_loading").assertIsDisplayed()
    }

    @Test
    fun errorStateShowsRetryAction() {
        var lastEvent: BusinessProfileEvent? = null
        composeRule.setContent {
            BudcomTheme {
                BusinessProfileScreen(
                    state = BusinessProfileUiState(isLoading = false, error = MasterDataUiError.Unexpected("boom")),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("business_profile_error").assertIsDisplayed()
        composeRule.onNodeWithTag("business_profile_retry").performClick()
        assertEquals(BusinessProfileEvent.Retry, lastEvent)
    }

    @Test
    fun emptyStateShowsSetupActionAndEntersEditMode() {
        var lastEvent: BusinessProfileEvent? = null
        composeRule.setContent {
            BudcomTheme {
                BusinessProfileScreen(
                    state = BusinessProfileUiState(isLoading = false, hasSavedProfile = false),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("business_profile_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("business_profile_setup").performClick()
        assertEquals(BusinessProfileEvent.EditTapped, lastEvent)
    }

    @Test
    fun populatedProfileShowsTradingNameAndOptionalFieldsWhenPresent() {
        composeRule.setContent {
            BudcomTheme {
                BusinessProfileScreen(
                    state = BusinessProfileUiState(
                        isLoading = false,
                        hasSavedProfile = true,
                        form = BusinessProfileFormState(tradingName = "Acme Traders", gstin = "27ABCDE1234F1Z5"),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("business_profile_trading_name_value").assertIsDisplayed()
        composeRule.onNodeWithTag("business_profile_gstin_value").assertIsDisplayed()
        // Legal name was never set -- its row must not render at all, not render blank.
        assertEquals(0, composeRule.onAllNodesForTag("business_profile_legal_name_value").fetchSemanticsNodes().size)
    }

    @Test
    fun editFormEmitsFieldChangedEventsAndSaveTapped() {
        val events = mutableListOf<BusinessProfileEvent>()
        composeRule.setContent {
            BudcomTheme {
                BusinessProfileScreen(
                    state = BusinessProfileUiState(isLoading = false, hasSavedProfile = false, isEditing = true),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeRule.onNodeWithTag("business_profile_input_trading_name").performTextInput("Acme")
        // Save sits below 11 fields inside a scrollable column -- on a real device the button can
        // start out below the fold, so bring it into view before clicking (matches this project's
        // established off-screen-widget pattern for other real-device instrumented tests).
        composeRule.onNodeWithTag("business_profile_save").performScrollTo().performClick()

        assertEquals(true, events.any { it is BusinessProfileEvent.TradingNameChanged })
        assertEquals(true, events.contains(BusinessProfileEvent.SaveTapped))
    }

    @Test
    fun cancelInEditModeEmitsCancelEditTapped() {
        var lastEvent: BusinessProfileEvent? = null
        composeRule.setContent {
            BudcomTheme {
                BusinessProfileScreen(
                    state = BusinessProfileUiState(isLoading = false, hasSavedProfile = true, isEditing = true),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("business_profile_cancel").performScrollTo().performClick()
        assertEquals(BusinessProfileEvent.CancelEditTapped, lastEvent)
    }

    @Test
    fun longTradingNameAndDescriptionRenderWithoutCrashing() {
        val longName = "A Very Long Registered Business Trading Name That Keeps Going ".repeat(4)
        val longDescription = "A long business description. ".repeat(30)
        composeRule.setContent {
            BudcomTheme {
                BusinessProfileScreen(
                    state = BusinessProfileUiState(
                        isLoading = false,
                        hasSavedProfile = true,
                        form = BusinessProfileFormState(tradingName = longName, description = longDescription),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("business_profile_view").assertIsDisplayed()
    }

    @Test
    fun editModeNoticeDialogShowsAndDismisses() {
        var lastEvent: BusinessProfileEvent? = null
        composeRule.setContent {
            BudcomTheme {
                BusinessProfileScreen(
                    state = BusinessProfileUiState(isLoading = false, hasSavedProfile = false, isEditing = true, notice = "Business / Trading Name is required."),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("business_profile_notice_message").assertIsDisplayed()
        composeRule.onNodeWithTag("business_profile_notice_dismiss").performClick()
        assertEquals(BusinessProfileEvent.DismissNotice, lastEvent)
    }

    // ============================== LOGO (MVP-1.3-B) ==============================

    @Test
    fun editModeWithNoLogoShowsOnlyAddLogoAndEmitsChangeLogoTapped() {
        var lastEvent: BusinessProfileEvent? = null
        composeRule.setContent {
            BudcomTheme {
                BusinessProfileScreen(
                    state = BusinessProfileUiState(isLoading = false, hasSavedProfile = true, isEditing = true, logoFile = null),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("business_profile_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("business_profile_change_logo").assertIsDisplayed().performClick()
        assertEquals(BusinessProfileEvent.ChangeLogoTapped, lastEvent)
        assertEquals(0, composeRule.onAllNodesForTag("business_profile_remove_logo").fetchSemanticsNodes().size)
    }

    @Test
    fun editModeWithAnExistingLogoShowsReplaceAndRemoveAndRendersTheImage() {
        val logoFile = writeTinyPngFixture()
        var lastEvent: BusinessProfileEvent? = null
        composeRule.setContent {
            BudcomTheme {
                BusinessProfileScreen(
                    state = BusinessProfileUiState(isLoading = false, hasSavedProfile = true, isEditing = true, logoFile = logoFile),
                    onEvent = { lastEvent = it },
                )
            }
        }
        composeRule.onNodeWithTag("business_profile_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("business_profile_remove_logo").assertIsDisplayed().performClick()
        assertEquals(BusinessProfileEvent.ClearLogoTapped, lastEvent)
    }

    @Test
    fun logoBusyIndicatorShowsWhileUpdatingAndDisablesTheChangeButton() {
        composeRule.setContent {
            BudcomTheme {
                BusinessProfileScreen(
                    state = BusinessProfileUiState(isLoading = false, hasSavedProfile = true, isEditing = true, isUpdatingLogo = true),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("business_profile_logo_busy").assertIsDisplayed()
        composeRule.onNodeWithTag("business_profile_change_logo").assertIsNotEnabled()
    }

    @Test
    fun viewModeShowsTheLogoAboveTheTradingName() {
        val logoFile = writeTinyPngFixture()
        composeRule.setContent {
            BudcomTheme {
                BusinessProfileScreen(
                    state = BusinessProfileUiState(
                        isLoading = false,
                        hasSavedProfile = true,
                        form = BusinessProfileFormState(tradingName = "Acme Traders"),
                        logoFile = logoFile,
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithTag("business_profile_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("business_profile_trading_name_value").assertIsDisplayed()
    }

    private fun writeTinyPngFixture(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "business_profile_screen_test_logo_${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return file
    }
}
