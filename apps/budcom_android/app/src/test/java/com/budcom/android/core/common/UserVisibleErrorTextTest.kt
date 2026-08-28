package com.budcom.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserVisibleErrorTextTest {
    @Test
    fun unexpectedSqliteConstraintNeverReachesUserCopy() {
        val error = AppError.Unexpected(
            RuntimeException("UNIQUE constraint failed: txn_order.orderId"),
        )
        val visible = UserVisibleErrorText.fromAppError(error)
        assertEquals(UserVisibleErrorText.UNEXPECTED, visible)
        assertFalse(visible.contains("UNIQUE", ignoreCase = true))
        assertFalse(visible.contains("SQLite", ignoreCase = true))
        assertFalse(visible.contains("txn_order"))
    }

    @Test
    fun javaExceptionClassNamesStayOutOfPresentation() {
        val error = AppError.Unexpected(IllegalStateException("java.lang.IllegalStateException: boom"))
        val visible = UserVisibleErrorText.fromAppError(error)
        assertEquals(UserVisibleErrorText.UNEXPECTED, visible)
        assertFalse(visible.contains("IllegalStateException"))
        assertFalse(visible.contains("java.lang"))
    }

    @Test
    fun sqliteExceptionMessageIsNotForwarded() {
        val visible = UserVisibleErrorText.fromThrowable(
            RuntimeException("android.database.sqlite.SQLiteException: database is locked (code 5 SQLITE_BUSY)"),
        )
        assertEquals(UserVisibleErrorText.UNEXPECTED, visible)
        assertFalse(visible.contains("SQLITE_BUSY"))
        assertFalse(visible.contains("locked"))
    }

    @Test
    fun stackLikeCopyIsRejected() {
        assertTrue(UserVisibleErrorText.looksTechnical("at com.budcom.android.Foo.kt:12"))
        val visible = UserVisibleErrorText.sanitizeOr(
            "Caused by: android.database.sqlite.SQLiteException: disk I/O error",
            UserVisibleErrorText.UNEXPECTED,
        )
        assertEquals(UserVisibleErrorText.UNEXPECTED, visible)
    }

    @Test
    fun httpStatusLineIsTreatedAsTechnical() {
        assertTrue(UserVisibleErrorText.looksTechnical("HTTP 503 Internal Server Error"))
        assertEquals(
            UserVisibleErrorText.CONNECTOR_UNAVAILABLE,
            UserVisibleErrorText.fromRemote(503, "HTTP 503 Internal Server Error"),
        )
    }

    @Test
    fun connectorHumanMessageIsKeptWhenNotTechnical() {
        val error = AppError.Remote(httpStatus = 409, code = "COMPANY_BUSY", message = "Company is open in Tally.")
        assertEquals("Company is open in Tally.", UserVisibleErrorText.fromAppError(error))
    }

    @Test
    fun typedOfflineAndTimeoutStayHuman() {
        assertEquals(UserVisibleErrorText.OFFLINE, UserVisibleErrorText.fromAppError(AppError.Offline()))
        assertEquals(UserVisibleErrorText.TIMEOUT, UserVisibleErrorText.fromAppError(AppError.Timeout()))
    }
}
