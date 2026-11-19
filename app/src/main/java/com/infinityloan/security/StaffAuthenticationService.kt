package com.infinityloan.security

import com.infinityloan.data.StaffAccountDao
import com.infinityloan.data.StaffAccountEntity
import java.util.Locale

data class StaffSession(
    val staffId: String,
    val username: String,
    val displayName: String,
    val profile: AccessProfile
)

enum class AuthenticationResult {
    AUTHENTICATED,
    INVALID_CREDENTIALS,
    ACCOUNT_DISABLED
}

/**
 * Device-local credential workflow. Multi-device production identity must use
 * the shared backend with server-side sessions and account protection.
 *
 * Password arrays are treated as short-lived secrets and cleared on every exit.
 */
class StaffAuthenticationService(
    private val staffAccounts: StaffAccountDao,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun createInitialAdministrator(
        username: String,
        displayName: String,
        password: CharArray
    ): Boolean {
        try {
            val normalizedUsername = normalizeUsername(username)
            require(normalizedUsername.length in 3..64) {
                "El usuario debe tener entre 3 y 64 caracteres."
            }
            require(displayName.isNotBlank()) { "El nombre no puede estar vacío." }
            require(password.size >= 12) {
                "La contraseña debe tener al menos 12 caracteres."
            }

            val hash = PasswordHasher.hash(password)
            return staffAccounts.createInitialAdministrator(
                StaffAccountEntity(
                    username = normalizedUsername,
                    displayName = displayName.trim(),
                    role = StaffRole.ADMINISTRATOR.name,
                    passwordAlgorithm = hash.algorithm,
                    passwordIterations = hash.iterations,
                    passwordSaltBase64 = hash.saltBase64,
                    passwordHashBase64 = hash.hashBase64
                )
            )
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun createStaffAccount(
        actor: StaffSession,
        username: String,
        displayName: String,
        role: StaffRole,
        password: CharArray,
        grants: Set<Permission> = emptySet(),
        denials: Set<Permission> = emptySet()
    ): Boolean {
        try {
            require(AccessPolicy.allows(actor.profile, Permission.STAFF_MANAGE)) {
                "No tienes permiso para administrar empleados."
            }
            require(role != StaffRole.ADMINISTRATOR || actor.profile.role == StaffRole.ADMINISTRATOR) {
                "Solo un administrador puede crear otra cuenta administradora."
            }
            require(
                grants.isEmpty() && denials.isEmpty() ||
                    AccessPolicy.allows(actor.profile, Permission.ROLE_PERMISSION_MANAGE)
            ) { "No tienes permiso para personalizar los permisos." }
            val targetProfile = AccessProfile(role, grants, denials)
            require(AccessPolicy.effectivePermissions(targetProfile).all {
                AccessPolicy.allows(actor.profile, it)
            }) { "No puedes crear un perfil con más permisos que el tuyo." }
            require((grants + denials).all { AccessPolicy.allows(actor.profile, it) }) {
                "No puedes asignar permisos que tu perfil no posee."
            }

            val normalizedUsername = normalizeUsername(username)
            require(normalizedUsername.length in 3..64) {
                "El usuario debe tener entre 3 y 64 caracteres."
            }
            require(displayName.isNotBlank()) { "El nombre no puede estar vacío." }
            require(password.size >= 12) {
                "La contraseña debe tener al menos 12 caracteres."
            }

            val hash = PasswordHasher.hash(password)
            staffAccounts.insert(
                StaffAccountEntity(
                    username = normalizedUsername,
                    displayName = displayName.trim(),
                    role = role.name,
                    grants = grants.sortedBy { it.name }.joinToString("|") { it.name },
                    denials = denials.sortedBy { it.name }.joinToString("|") { it.name },
                    passwordAlgorithm = hash.algorithm,
                    passwordIterations = hash.iterations,
                    passwordSaltBase64 = hash.saltBase64,
                    passwordHashBase64 = hash.hashBase64
                )
            )
            return true
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun setStaffAccountActive(
        actor: StaffSession,
        staffId: String,
        active: Boolean
    ) {
        require(AccessPolicy.allows(actor.profile, Permission.STAFF_MANAGE)) {
            "No tienes permiso para administrar empleados."
        }
        require(active || actor.staffId != staffId) {
            "No puedes desactivar tu propia cuenta."
        }
        val target = staffAccounts.getAll().firstOrNull { it.id == staffId }
            ?: throw IllegalArgumentException("La cuenta seleccionada no existe.")
        require(
            target.role != StaffRole.ADMINISTRATOR.name ||
                actor.profile.role == StaffRole.ADMINISTRATOR
        ) { "Solo un administrador puede modificar otra cuenta administradora." }
        if (!active && target.role == StaffRole.ADMINISTRATOR.name && target.isActive) {
            val activeAdministrators = staffAccounts.getAll().count {
                it.role == StaffRole.ADMINISTRATOR.name && it.isActive
            }
            require(activeAdministrators > 1) {
                "No puedes desactivar el último administrador activo."
            }
        }
        staffAccounts.setActive(staffId, active)
    }

    suspend fun authenticate(
        username: String,
        password: CharArray
    ): Pair<AuthenticationResult, StaffSession?> {
        try {
            val normalizedUsername = normalizeUsername(username)
            if (normalizedUsername.isBlank() || password.isEmpty()) {
                return AuthenticationResult.INVALID_CREDENTIALS to null
            }

            val account = staffAccounts.findByUsername(normalizedUsername)
                ?: return AuthenticationResult.INVALID_CREDENTIALS to null

            val storedHash = PasswordHash(
                algorithm = account.passwordAlgorithm,
                iterations = account.passwordIterations,
                saltBase64 = account.passwordSaltBase64,
                hashBase64 = account.passwordHashBase64
            )
            if (!PasswordHasher.verify(password, storedHash)) {
                return AuthenticationResult.INVALID_CREDENTIALS to null
            }
            if (!account.isActive) {
                return AuthenticationResult.ACCOUNT_DISABLED to null
            }

            val role = runCatching { StaffRole.valueOf(account.role) }.getOrNull()
                ?: return AuthenticationResult.INVALID_CREDENTIALS to null
            val profile = AccessProfile(
                role = role,
                grants = parsePermissions(account.grants),
                denials = parsePermissions(account.denials)
            )
            staffAccounts.updateLastLogin(account.id, clock())
            return AuthenticationResult.AUTHENTICATED to StaffSession(
                staffId = account.id,
                username = account.username,
                displayName = account.displayName,
                profile = profile
            )
        } finally {
            password.fill('\u0000')
        }
    }

    private fun normalizeUsername(username: String): String =
        username.trim().lowercase(Locale.ROOT)

    private fun parsePermissions(encoded: String): Set<Permission> =
        encoded.split('|').mapNotNull { token ->
            runCatching { Permission.valueOf(token) }.getOrNull()
        }.toSet()
}
