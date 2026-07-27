package com.budcom.android.feature.serverconfig.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorUrlValidatorTest {

    @Test
    fun `normalizes trailing slash and default scheme host`() {
        val result = ConnectorUrlValidator.validate("http://10.0.2.2:8080")
        assertEquals(
            ConnectorUrlValidator.Result.Valid("http://10.0.2.2:8080/"),
            result,
        )
    }

    @Test
    fun `accepts https origin`() {
        val result = ConnectorUrlValidator.validate("https://connector.example.com/")
        assertTrue(result is ConnectorUrlValidator.Result.Valid)
        assertEquals(
            "https://connector.example.com/",
            (result as ConnectorUrlValidator.Result.Valid).normalized,
        )
    }

    @Test
    fun `rejects blank path segments beyond root`() {
        val result = ConnectorUrlValidator.validate("http://10.0.2.2:8080/api")
        assertEquals(
            ConnectorUrlValidator.Result.Invalid(ConnectorUrlValidator.Reason.PATH_NOT_ALLOWED),
            result,
        )
    }

    @Test
    fun `rejects unsupported scheme and blank`() {
        assertEquals(
            ConnectorUrlValidator.Result.Invalid(ConnectorUrlValidator.Reason.BLANK),
            ConnectorUrlValidator.validate("   "),
        )
        assertEquals(
            ConnectorUrlValidator.Result.Invalid(ConnectorUrlValidator.Reason.UNSUPPORTED_SCHEME),
            ConnectorUrlValidator.validate("ftp://10.0.2.2:8080/"),
        )
    }

    @Test
    fun `rejects query and fragment`() {
        assertTrue(
            ConnectorUrlValidator.validate("http://10.0.2.2:8080/?x=1")
                is ConnectorUrlValidator.Result.Invalid,
        )
    }
}
