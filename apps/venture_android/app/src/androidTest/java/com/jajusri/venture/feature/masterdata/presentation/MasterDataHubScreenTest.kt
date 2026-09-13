package com.jajusri.venture.feature.masterdata.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jajusri.venture.ui.theme.VentureTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MasterDataHubScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun opensLedgersAndStockItems() {
        var openedLedgers = false
        var openedStockItems = false
        composeRule.setContent {
            VentureTheme {
                MasterDataHubScreen(
                    onOpenLedgers = { openedLedgers = true },
                    onOpenStockItems = { openedStockItems = true },
                )
            }
        }
        composeRule.onNodeWithTag("master_data_hub").assertIsDisplayed()
        composeRule.onNodeWithTag("master_data_open_ledgers").performClick()
        composeRule.onNodeWithTag("master_data_open_stock_items").performClick()
        assertTrue(openedLedgers)
        assertTrue(openedStockItems)
    }
}
