package com.infinityloan.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuditLogDaoTest {
    private lateinit var database: InfinityDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            InfinityDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun cleanup() {
        database.close()
    }

    @Test
    fun auditEventsAreStoredAndReturnedNewestFirst() = runBlocking {
        database.auditLogs().insert(
            AuditLogEntity(
                actorStaffId = "staff-1",
                actorUsername = "admin",
                action = "STAFF_ACCOUNT_CREATED",
                targetType = "STAFF_ACCOUNT",
                targetId = "staff-2",
                details = "username=cashier;role=CASHIER",
                createdAt = 100L
            )
        )
        database.auditLogs().insert(
            AuditLogEntity(
                actorStaffId = "staff-1",
                actorUsername = "admin",
                action = "STAFF_ACCOUNT_DISABLED",
                targetType = "STAFF_ACCOUNT",
                targetId = "staff-2",
                details = "username=cashier",
                createdAt = 200L
            )
        )

        val events = database.auditLogs().observeRecent(10).first()

        assertEquals(2, events.size)
        assertEquals("STAFF_ACCOUNT_DISABLED", events[0].action)
        assertEquals("STAFF_ACCOUNT_CREATED", events[1].action)
        assertTrue(events.none { it.details.contains("password", ignoreCase = true) })
    }

    @Test
    fun auditQueryHonorsRequestedLimit() = runBlocking {
        repeat(5) { index ->
            database.auditLogs().insert(
                AuditLogEntity(
                    actorStaffId = "staff-1",
                    actorUsername = "admin",
                    action = "TEST_EVENT_$index",
                    targetType = "TEST",
                    targetId = null,
                    details = "",
                    createdAt = index.toLong()
                )
            )
        }

        assertEquals(3, database.auditLogs().observeRecent(3).first().size)
        assertEquals(2, database.auditLogs().findByActor("staff-1", 2).size)
    }
}
