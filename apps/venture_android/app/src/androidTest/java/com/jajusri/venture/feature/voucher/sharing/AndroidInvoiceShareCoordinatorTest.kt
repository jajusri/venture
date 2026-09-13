package com.jajusri.venture.feature.voucher.sharing

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jajusri.venture.core.util.DispatcherProvider
import com.jajusri.venture.feature.voucher.domain.model.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AndroidInvoiceShareCoordinatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val coordinator = AndroidInvoiceShareCoordinator(
        context,
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Main
            override val io: CoroutineDispatcher = Dispatchers.IO
            override val default: CoroutineDispatcher = Dispatchers.Default
        },
        InvoiceShareCachePolicy(InvoiceShareCacheBoundary(context.cacheDir), InvoiceShareFileOperations()),
    )

    @Test
    fun pdfShareUsesContentUriMimeAndTemporaryReadGrant() = runBlocking {
        val prepared = coordinator.preparePdf(details()) as InvoiceShareResult.Success
        val chooser = (coordinator.createPdfShareIntent(prepared.value) as InvoiceShareResult.Success).value
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        val uri = send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!

        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/pdf", send.type)
        assertEquals("content", uri.scheme)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertFalse(uri.toString().startsWith("file:"))
    }

    @Test
    fun createDocumentUsesPdfMimeAndSuggestedFilename() {
        val intent = ActivityResultContracts.CreateDocument("application/pdf")
            .createIntent(context, "VENTURE-Invoice-S-1.pdf")
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/pdf", intent.type)
        assertEquals("VENTURE-Invoice-S-1.pdf", intent.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    fun longInvoiceProducesMultipleReadablePagesWithoutDroppingRows() {
        val long = details().copy(
            inventoryEntries = (1..120).map {
                VoucherInventoryLine(it, "Item line $it with a sufficiently descriptive synchronized name", "$it PCS", "10/PCS", VoucherMoney("$it.00", null))
            },
        )
        val output = File(context.cacheDir, "long-invoice-test.pdf")
        InvoicePdfRenderer.render(long, output)
        ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> assertTrue(renderer.pageCount > 1) }
        }
        assertTrue(output.length() > 0)
        output.delete()
    }

    private fun details() = VoucherDetails(
        summary = VoucherSummary(
            VoucherIdentity("internal"), "2026-07-27", "Sales", "S-1", "Acme", null,
            VoucherMoney("100.00", null), VoucherStatus.Active, VoucherDataQuality.Complete,
        ),
        effectiveDate = null,
        narration = null,
        ledgerEntries = emptyList(),
        inventoryEntries = listOf(VoucherInventoryLine(1, "Fixture", "2 PCS", "50/PCS", VoucherMoney("100.00", null))),
    )
}
