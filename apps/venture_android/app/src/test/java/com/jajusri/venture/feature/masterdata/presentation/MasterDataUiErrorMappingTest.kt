package com.jajusri.venture.feature.masterdata.presentation

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.UserVisibleErrorText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterDataUiErrorMappingTest {
    @Test
    fun unexpectedSqliteConstraintNeverReachesUiMessage() {
        val mapped = AppError.Unexpected(
            RuntimeException("UNIQUE constraint failed: txn_order.orderId"),
        ).toMasterDataUiError() as MasterDataUiError.Unexpected
        assertEquals(UserVisibleErrorText.UNEXPECTED, mapped.message)
        assertFalse(mapped.message.contains("UNIQUE"))
        assertFalse(mapped.message.contains("txn_order"))
        assertFalse(mapped.message.contains("SQLite", ignoreCase = true))
    }

    @Test
    fun remoteHttpStatusPrefixIsNotShown() {
        val mapped = AppError.Remote(
            httpStatus = 503,
            code = "SERVICE_UNAVAILABLE",
            message = "HTTP 503 Internal Server Error",
        ).toMasterDataUiError() as MasterDataUiError.Remote
        assertEquals(UserVisibleErrorText.CONNECTOR_UNAVAILABLE, mapped.message)
        assertFalse(mapped.message.contains("HTTP 503"))
        assertFalse(mapped.message.contains("Internal Server Error"))
    }

    @Test
    fun humanRemoteCopyIsKept() {
        val mapped = AppError.Remote(
            httpStatus = 409,
            code = "COMPANY_BUSY",
            message = "Company is open in Tally.",
        ).toMasterDataUiError() as MasterDataUiError.Remote
        assertEquals("Company is open in Tally.", mapped.message)
    }

    @Test
    fun displayMessageUsesMappedCopy() {
        val error = AppError.Unexpected(IllegalStateException("java.lang.IllegalStateException: boom"))
            .toMasterDataUiError()
        val visible = error.displayMessage()
        assertEquals(UserVisibleErrorText.UNEXPECTED, visible)
        assertTrue(error is MasterDataUiError.Unexpected)
        assertFalse(visible.contains("IllegalStateException"))
    }
}
