package com.budcom.android.feature.masterdata.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.budcom.android.ui.theme.BudcomTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MasterDataHubScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun opensLedgersAndKeepsStockItemsDisabled() {
        var opened = false
        composeRule.setContent {
            BudcomTheme {
                MasterDataHubScreen(onOpenLedgers = { opened = true })
            }
        }
        composeRule.onNodeWithTag("master_data_hub").assertIsDisplayed()
        composeRule.onNodeWithTag("master_data_stock_items_future").assertIsNotEnabled()
        composeRule.onNodeWithTag("master_data_open_ledgers").performClick()
        assertTrue(opened)
    }
}
