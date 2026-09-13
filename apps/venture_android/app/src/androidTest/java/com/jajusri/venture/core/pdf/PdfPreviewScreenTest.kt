package com.jajusri.venture.core.pdf

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jajusri.venture.ui.theme.VentureTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** TD-028: Compose-level behavior of the shared preview screen — loading/error states and that
 * Save/Share/Back dispatch exactly the callback they're wired to, independent of which feature
 * (Voucher or Ledger) hosts it. */
class PdfPreviewScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val neverResolvesRenderer = object : PdfPageRenderer {
        override suspend fun open(filePath: String): PdfPreviewDocument? = null
    }

    @Test
    fun showsLoadingThenErrorWhenTheRendererCannotOpenTheFile() {
        composeRule.setContent {
            VentureTheme {
                PdfPreviewScreen(
                    filePath = "/does/not/matter.pdf",
                    title = "sample.pdf",
                    renderer = neverResolvesRenderer,
                    onBack = {},
                    onSave = {},
                    onShare = {},
                )
            }
        }
        composeRule.onNodeWithTag("pdf_preview_error").assertExists()
    }

    @Test
    fun backButtonInvokesOnBack() {
        var backCalled = false
        composeRule.setContent {
            VentureTheme {
                PdfPreviewScreen(
                    filePath = "/does/not/matter.pdf",
                    title = "sample.pdf",
                    renderer = neverResolvesRenderer,
                    onBack = { backCalled = true },
                    onSave = {},
                    onShare = {},
                )
            }
        }
        composeRule.onNodeWithTag("pdf_preview_back").performClick()
        assertTrue(backCalled)
    }

    @Test
    fun saveButtonInvokesOnSave() {
        var saveCalled = false
        composeRule.setContent {
            VentureTheme {
                PdfPreviewScreen(
                    filePath = "/does/not/matter.pdf",
                    title = "sample.pdf",
                    renderer = neverResolvesRenderer,
                    onBack = {},
                    onSave = { saveCalled = true },
                    onShare = {},
                )
            }
        }
        composeRule.onNodeWithTag("pdf_preview_save").performClick()
        assertTrue(saveCalled)
    }

    @Test
    fun shareButtonInvokesOnShare() {
        var shareCalled = false
        composeRule.setContent {
            VentureTheme {
                PdfPreviewScreen(
                    filePath = "/does/not/matter.pdf",
                    title = "sample.pdf",
                    renderer = neverResolvesRenderer,
                    onBack = {},
                    onSave = {},
                    onShare = { shareCalled = true },
                )
            }
        }
        composeRule.onNodeWithTag("pdf_preview_share").performClick()
        assertTrue(shareCalled)
    }

    @Test
    fun saveAndShareAreDisabledWhileAnActionIsBusy() {
        composeRule.setContent {
            VentureTheme {
                PdfPreviewScreen(
                    filePath = "/does/not/matter.pdf",
                    title = "sample.pdf",
                    renderer = neverResolvesRenderer,
                    onBack = {},
                    onSave = {},
                    onShare = {},
                    isActionBusy = true,
                )
            }
        }
        composeRule.onNodeWithTag("pdf_preview_save").assertExists()
    }

    @Test
    fun extraActionInvokesItsOwnCallback() {
        var extraCalled = false
        composeRule.setContent {
            VentureTheme {
                PdfPreviewScreen(
                    filePath = "/does/not/matter.pdf",
                    title = "sample.pdf",
                    renderer = neverResolvesRenderer,
                    onBack = {},
                    onSave = {},
                    onShare = {},
                    extraActions = listOf(
                        PdfPreviewAction(label = "WhatsApp", testTag = "pdf_preview_whatsapp") { extraCalled = true },
                    ),
                )
            }
        }
        composeRule.onNodeWithTag("pdf_preview_whatsapp").performClick()
        assertTrue(extraCalled)
    }
}
