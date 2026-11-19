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
