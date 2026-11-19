package com.infinityloan.security

import com.infinityloan.data.StaffAccountDao
import com.infinityloan.data.StaffAccountEntity
import com.infinityloan.data.StaffAccountSummary
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

        override suspend fun getAllSummaries(): List<StaffAccountSummary> =
            accounts.values.sortedBy { it.displayName }.map {
                StaffAccountSummary(
                    id = it.id,
                    username = it.username,
                    displayName = it.displayName,
                    role = it.role,
                    isActive = it.isActive,
                    createdAt = it.createdAt,
                    lastLoginAt = it.lastLoginAt
                )
            }

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


    @Test
    fun clearsPasswordBuffersAfterAdministratorProvisioningAndLogin() = runBlocking {
        val dao = FakeStaffAccountDao()
        val service = StaffAuthenticationService(dao)
        val initialPassword = "UnaClaveMuySegura-2026".toCharArray()

        assertTrue(
            service.createInitialAdministrator(
                "admin",
                "Administración",
                initialPassword
            )
        )
        assertTrue(initialPassword.all { it.code == 0 })

        val loginPassword = "UnaClaveMuySegura-2026".toCharArray()
        val (result, _) = service.authenticate("admin", loginPassword)
        assertEquals(AuthenticationResult.AUTHENTICATED, result)
        assertTrue(loginPassword.all { it.code == 0 })
    }

    @Test
    fun rejectsBlankUsernameWithoutLookingUpAnAccountAndClearsPassword() = runBlocking {
        val dao = FakeStaffAccountDao()
        val password = "UnaClaveMuySegura-2026".toCharArray()

        val (result, session) = StaffAuthenticationService(dao)
            .authenticate("   ", password)

        assertEquals(AuthenticationResult.INVALID_CREDENTIALS, result)
        assertNull(session)
        assertTrue(password.all { it.code == 0 })
    }


    @Test
    fun administratorCanCreateCashierAndPasswordIsCleared() = runBlocking {
        val dao = FakeStaffAccountDao()
        val service = StaffAuthenticationService(dao)
        service.createInitialAdministrator(
            "admin",
            "Administración",
            "UnaClaveMuySegura-2026".toCharArray()
        )
        val (_, admin) = service.authenticate(
            "admin",
            "UnaClaveMuySegura-2026".toCharArray()
        )
        val password = "ClaveCajeroMuySegura-2026".toCharArray()

        assertTrue(
            service.createStaffAccount(
                actor = requireNotNull(admin),
                username = "  CAJERO  ",
                displayName = "Cajero principal",
                role = StaffRole.CASHIER,
                password = password
            )
        )
        assertTrue(password.all { it.code == 0 })
        assertEquals(StaffRole.CASHIER.name, dao.accounts["cajero"]?.role)
    }

    @Test
    fun cashierCannotProvisionStaffAccounts() = runBlocking {
        val dao = FakeStaffAccountDao()
        val service = StaffAuthenticationService(dao)
        service.createInitialAdministrator(
            "admin",
            "Administración",
            "UnaClaveMuySegura-2026".toCharArray()
        )
        val admin = requireNotNull(
            service.authenticate("admin", "UnaClaveMuySegura-2026".toCharArray()).second
        )
        service.createStaffAccount(
            actor = admin,
            username = "cashier",
            displayName = "Cajero",
            role = StaffRole.CASHIER,
            password = "ClaveCajeroMuySegura-2026".toCharArray()
        )
        val cashier = requireNotNull(
            service.authenticate("cashier", "ClaveCajeroMuySegura-2026".toCharArray()).second
        )

        val password = "OtraClaveMuySegura-2026".toCharArray()
        try {
            service.createStaffAccount(
                actor = cashier,
                username = "intruso",
                displayName = "No autorizado",
                role = StaffRole.CASHIER,
                password = password
            )
            throw AssertionError("La cuenta de cajero no debe crear empleados.")
        } catch (_: IllegalArgumentException) {
            assertTrue(password.all { it.code == 0 })
        }
        assertNull(dao.accounts["intruso"])
    }


    @Test
    fun supervisorCannotCreateRoleWithPermissionsBeyondOwnProfile() = runBlocking {
        val dao = FakeStaffAccountDao()
        val service = StaffAuthenticationService(dao)
        service.createInitialAdministrator(
            "admin",
            "Administración",
            "UnaClaveMuySegura-2026".toCharArray()
        )
        val admin = requireNotNull(
            service.authenticate("admin", "UnaClaveMuySegura-2026".toCharArray()).second
        )
        service.createStaffAccount(
            actor = admin,
            username = "supervisor",
            displayName = "Supervisor",
            role = StaffRole.SUPERVISOR,
            password = "ClaveSupervisorMuySegura-2026".toCharArray()
        )
        val supervisor = requireNotNull(
            service.authenticate("supervisor", "ClaveSupervisorMuySegura-2026".toCharArray()).second
        )
        val password = "ClaveCajeroMuySegura-2026".toCharArray()

        try {
            service.createStaffAccount(
                actor = supervisor,
                username = "cashier",
                displayName = "Cajero",
                role = StaffRole.CASHIER,
                password = password
            )
            throw AssertionError("No debe permitirse elevar privilegios.")
        } catch (_: IllegalArgumentException) {
            assertTrue(password.all { it.code == 0 })
        }
        assertNull(dao.accounts["cashier"])
    }


    @Test
    fun staffCannotDeactivateTheirOwnAccount() = runBlocking {
        val dao = FakeStaffAccountDao()
        val service = StaffAuthenticationService(dao)
        service.createInitialAdministrator(
            "admin",
            "Administración",
            "UnaClaveMuySegura-2026".toCharArray()
        )
        val admin = requireNotNull(
            service.authenticate("admin", "UnaClaveMuySegura-2026".toCharArray()).second
        )

        try {
            service.setStaffAccountActive(admin, admin.staffId, false)
            throw AssertionError("No debe permitirse desactivar la propia cuenta.")
        } catch (_: IllegalArgumentException) {
            assertTrue(dao.accounts.getValue("admin").isActive)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun requiresStrongInitialPassword() {
        runBlocking {
            StaffAuthenticationService(FakeStaffAccountDao()).createInitialAdministrator(
                "admin",
                "Administración",
                "short".toCharArray()
            )
        }
    }
}
