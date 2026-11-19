package com.infinityloan.security

import com.infinityloan.data.StaffAccountDao
import com.infinityloan.data.StaffAccountEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffAuthenticationServiceTest {

    private class FakeStaffAccountDao : StaffAccountDao {
        val accounts = linkedMapOf<String, StaffAccountEntity>()
        var lastLogin: Pair<String, Long>? = null

        override suspend fun findByUsername(username: String): StaffAccountEntity? =
            accounts[username]

        override suspend fun count(): Int = accounts.size

        override suspend fun insert(account: StaffAccountEntity) {
            check(accounts.putIfAbsent(account.username, account) == null)
        }

        override suspend fun updateLastLogin(id: String, timestamp: Long) {
            lastLogin = id to timestamp
        }

        override suspend fun setActive(id: String, active: Boolean) {
            val entry = accounts.entries.firstOrNull { it.value.id == id } ?: return
            accounts[entry.key] = entry.value.copy(isActive = active)
        }

        override suspend fun createInitialAdministrator(account: StaffAccountEntity): Boolean {
            if (accounts.isNotEmpty()) return false
            insert(account)
            return true
        }
    }

    @Test
    fun createsOnlyOneInitialAdministratorAndNormalizesUsername() = runBlocking {
        val dao = FakeStaffAccountDao()
        val service = StaffAuthenticationService(dao)

        assertTrue(
            service.createInitialAdministrator(
                "  ADMIN  ",
                "Administrador principal",
                "UnaClaveMuySegura-2026".toCharArray()
            )
        )
        assertFalse(
            service.createInitialAdministrator(
                "second-admin",
                "Segundo administrador",
                "OtraClaveMuySegura-2026".toCharArray()
            )
        )
        assertNotNull(dao.accounts["admin"])
        assertEquals(StaffRole.ADMINISTRATOR.name, dao.accounts["admin"]?.role)
    }

    @Test
    fun authenticatesAndBuildsRoleProfileFromStoredAccount() = runBlocking {
        val dao = FakeStaffAccountDao()
        val service = StaffAuthenticationService(dao) { 123456L }
        service.createInitialAdministrator(
            "Admin",
            "Administración",
            "UnaClaveMuySegura-2026".toCharArray()
        )

        val (result, session) = service.authenticate(
            " ADMIN ",
            "UnaClaveMuySegura-2026".toCharArray()
        )

        assertEquals(AuthenticationResult.AUTHENTICATED, result)
        assertNotNull(session)
        assertEquals(StaffRole.ADMINISTRATOR, session?.profile?.role)
        assertEquals(123456L, dao.lastLogin?.second)
    }

    @Test
    fun rejectsWrongPasswordAndDisabledAccount() = runBlocking {
        val dao = FakeStaffAccountDao()
        val service = StaffAuthenticationService(dao)
        service.createInitialAdministrator(
            "admin",
            "Administración",
            "UnaClaveMuySegura-2026".toCharArray()
        )

        val (wrongResult, wrongSession) = service.authenticate(
            "admin",
            "ClaveIncorrecta-2026".toCharArray()
        )
        assertEquals(AuthenticationResult.INVALID_CREDENTIALS, wrongResult)
        assertNull(wrongSession)

        val account = dao.accounts.getValue("admin")
        dao.setActive(account.id, false)
        val (disabledResult, disabledSession) = service.authenticate(
            "admin",
            "UnaClaveMuySegura-2026".toCharArray()
        )
        assertEquals(AuthenticationResult.ACCOUNT_DISABLED, disabledResult)
        assertNull(disabledSession)
    }

    @Test(expected = IllegalArgumentException::class)
    fun requiresStrongInitialPassword() = runBlocking {
        StaffAuthenticationService(FakeStaffAccountDao()).createInitialAdministrator(
            "admin",
            "Administración",
            "short".toCharArray()
        )
    }
}
