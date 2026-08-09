package com.budcom.android.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class DynamicBaseUrlInterceptorTest {

    @Test
    fun `rewrites host port to configured base URL`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("ok"))
        server.start()
        try {
            val provider = DefaultConnectorBaseUrlProvider().apply {
                updateInMemory(server.url("/").toString())
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(DynamicBaseUrlInterceptor(provider))
                .build()
            val response = client.newCall(
                Request.Builder()
                    .url("http://10.0.2.2:8080/health")
                    .build(),
            ).execute()
            assertEquals(200, response.code)
            val recorded = server.takeRequest()
            assertEquals("/health", recorded.path)
            assertEquals(server.port, recorded.requestUrl?.port)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `unconfigured endpoint fails locally without attempting a bootstrap host`() {
        val provider = DefaultConnectorBaseUrlProvider().apply { updateInMemory("") }
        val client = OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor(provider))
            .build()

        assertThrows(IOException::class.java) {
            client.newCall(Request.Builder().url("http://localhost/health").build()).execute()
        }
    }
}
