package com.budcom.android.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budcom.android.feature.transaction.data.local.CanonicalOrderEntity
import com.budcom.android.feature.transaction.data.local.CanonicalOrderLineEntity
import com.budcom.android.feature.transaction.data.local.OrderCommercialEventEntity
import com.budcom.android.feature.transaction.data.local.RoomCommercialDbTransaction
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommercialLifecycleAtomicityTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun lineFailureRollsBackNewOrderHeader() = runBlocking {
        val tx = RoomCommercialDbTransaction(db)
        val dao = db.canonicalOrderDao()
        runCatching {
            tx.run {
                dao.insert(header("order-1"))
                error("injected line failure")
                dao.upsertLines(listOf(line("order-1")))
            }
        }
        assertNull(dao.findById("co-1", "order-1"))
    }

    @Test
    fun archiveLineFailureRollsBackArchiveHeader() = runBlocking {
        val tx = RoomCommercialDbTransaction(db)
        val archive = db.orderVersionArchiveDao()
        runCatching {
            tx.run {
                archive.insertOrder(
                    com.budcom.android.feature.transaction.data.local.OrderVersionArchiveEntity(
                        "co-1", "order-1", 1, "k", "seller", "buyer", "SEEN", "CATALOGUE", "ESTIMATE",
                        null, 1, "DeviceLocalProvisional", null, null, 2, "DeviceLocalProvisional",
                    ),
                )
                error("injected archive line failure")
            }
        }
        assertNull(archive.findOrder("co-1", "order-1", 1))
    }

    @Test
    fun eventInsertFailureLeavesStateUnchanged() = runBlocking {
        val dao = db.canonicalOrderDao()
        dao.insert(header("order-2", "SEEN"))
        val tx = RoomCommercialDbTransaction(db)
        runCatching {
            tx.run {
                db.orderCommercialEventDao().insert(
                    OrderCommercialEventEntity(
                        "co-1", "evt-1", "key-1", "order-2", 1, "CONFIRMED", "seller", "actor", "device",
                        "buyer", 3, "DeviceLocalProvisional", 1, "confirm_orders",
                    ),
                )
                error("injected state failure")
                dao.updateState("co-1", "order-2", "CONFIRMED")
            }
        }
        assertTrue(dao.findById("co-1", "order-2")?.state == "SEEN")
        assertNull(db.orderCommercialEventDao().findByIdempotencyKey("co-1", "key-1"))
    }

    @Test
    fun restartSeesCompleteOldOrCompleteNewStateNeverHalf() = runBlocking {
        val dao = db.canonicalOrderDao()
        val tx = RoomCommercialDbTransaction(db)
        tx.run {
            dao.insert(header("order-3"))
            dao.upsertLines(listOf(line("order-3")))
        }
        assertTrue(dao.findById("co-1", "order-3") != null)
        assertTrue(dao.findLines("co-1", "order-3").isNotEmpty())
        runCatching {
            tx.run {
                dao.updateState("co-1", "order-3", "SENT")
                error("injected")
            }
        }
        assertTrue(dao.findById("co-1", "order-3")?.state == "DRAFT")
        assertTrue(dao.findLines("co-1", "order-3").isNotEmpty())
    }

    private fun header(orderId: String, state: String = "DRAFT") = CanonicalOrderEntity(
        companyId = "co-1", orderId = orderId, creationKey = "k-$orderId", sellerCompanyId = "co-1",
        buyerPartyId = "buyer", state = state, source = "CATALOGUE", submissionType = "ESTIMATE",
        note = null, createdAt = 1, createdAtSource = "DeviceLocalProvisional", version = 1,
    )

    private fun line(orderId: String) = CanonicalOrderLineEntity(
        "co-1", orderId, "line-1", "p1", "Widget", "Nos", "SKU", "1", "1", "INR", "ACTUAL", "1",
    )
}
