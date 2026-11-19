package com.infinityloan.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StaffAccountDaoTest {
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
    fun failedLoginCounterLocksAccountAndCanBeReset() = runBlocking {
        val account = StaffAccountEntity(
            username = "admin",
            displayName = "Administrador",
            role = "ADMINISTRATOR",
            passwordAlgorithm = "PBKDF2_SHA256_V1",
            passwordIterations = 210000,
            passwordSaltBase64 = "c2FsdA==",
            passwordHashBase64 = "aGFzaA=="
        )
        database.staffAccounts().insert(account)

        repeat(5) {
            database.staffAccounts().recordFailedLogin(
                id = account.id,
                now = 1000L,
                maxAttempts = 5,
                lockUntil = 901000L
            )
        }
        val locked = database.staffAccounts().findById(account.id)
        assertNotNull(locked)
        assertEquals(5, locked?.failedLoginAttempts)
        assertEquals(901000L, locked?.lockedUntil)

        database.staffAccounts().recordFailedLogin(
            id = account.id,
            now = 2000L,
            maxAttempts = 5,
            lockUntil = 902000L
        )
        assertEquals(5, database.staffAccounts().findById(account.id)?.failedLoginAttempts)

        database.staffAccounts().resetFailedLogins(account.id)
        val reset = database.staffAccounts().findById(account.id)
        assertEquals(0, reset?.failedLoginAttempts)
        assertNull(reset?.lockedUntil)
    }
}
