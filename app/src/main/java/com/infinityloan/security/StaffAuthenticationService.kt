package com.infinityloan.security

import com.infinityloan.data.StaffAccountDao
import com.infinityloan.data.AuditLogEntity
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
    ACCOUNT_DISABLED,
    ACCOUNT_LOCKED
}

/**
 * Device-local credential workflow. Multi-device production identity must use
 * the shared backend with server-side sessions and account protection.
 *
 * Password arrays are treated as short-lived secrets and cleared on every exit.
 */
class StaffAuthenticationService(
    private val staffAccounts: StaffAccountDao,
    private val audit: suspend (AuditLogEntity) -> Unit = {},
    private val persistStaffChange: suspend (suspend () -> Unit) -> Unit = { operation -> operation() },
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
            require(isSessionValid(actor)) { "La sesión del empleado ya no está activa." }
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
            val account = StaffAccountEntity(
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
            persistStaffChange {
                staffAccounts.insert(account)
                audit(AuditLogEntity(
                    actorStaffId = actor.staffId,
                    actorUsername = actor.username,
                    action = "STAFF_ACCOUNT_CREATED",
                    targetType = "STAFF_ACCOUNT",
                    targetId = account.id,
                    details = "username=$normalizedUsername;role=${role.name};grants=${account.grants};denials=${account.denials}"
                ))
            }
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
        require(isSessionValid(actor)) { "La sesión del empleado ya no está activa." }
        require(AccessPolicy.allows(actor.profile, Permission.STAFF_MANAGE)) {
            "No tienes permiso para administrar empleados."
        }
        require(active || actor.staffId != staffId) {
            "No puedes desactivar tu propia cuenta."
        }
        val target = staffAccounts.getAllSummaries().firstOrNull { it.id == staffId }
            ?: throw IllegalArgumentException("La cuenta seleccionada no existe.")
        require(
            target.role != StaffRole.ADMINISTRATOR.name ||
                actor.profile.role == StaffRole.ADMINISTRATOR
        ) { "Solo un administrador puede modificar otra cuenta administradora." }
        if (!active && target.role == StaffRole.ADMINISTRATOR.name && target.isActive) {
            val activeAdministrators = staffAccounts.getAllSummaries().count {
                it.role == StaffRole.ADMINISTRATOR.name && it.isActive
            }
            require(activeAdministrators > 1) {
                "No puedes desactivar el último administrador activo."
            }
        }
        persistStaffChange {
            val result = staffAccounts.setActiveSafely(staffId, active)
            require(result != -1) { "No puedes desactivar el último administrador activo." }
            require(result > 0) { "La cuenta ya no existe." }
            if (target.isActive != active) {
                audit(AuditLogEntity(
                    actorStaffId = actor.staffId,
                    actorUsername = actor.username,
                    action = if (active) "STAFF_ACCOUNT_ENABLED" else "STAFF_ACCOUNT_DISABLED",
                    targetType = "STAFF_ACCOUNT",
                    targetId = staffId,
                    details = "username=${target.username};role=${target.role};active=$active"
                ))
            }
        }
    }


    suspend fun updateStaffPermissions(
        actor: StaffSession,
        staffId: String,
        grants: Set<Permission>,
        denials: Set<Permission>
    ) {
        require(isSessionValid(actor)) { "La sesión del empleado ya no está activa." }
        require(AccessPolicy.allows(actor.profile, Permission.ROLE_PERMISSION_MANAGE)) {
            "No tienes permiso para personalizar permisos."
        }
        val target = staffAccounts.findById(staffId)
            ?: throw IllegalArgumentException("La cuenta seleccionada no existe.")
        val role = runCatching { StaffRole.valueOf(target.role) }.getOrNull()
            ?: throw IllegalArgumentException("El perfil de la cuenta no es válido.")
        require(target.id != actor.staffId) {
            "No puedes cambiar tus propios permisos durante la sesión."
        }
        require((grants + denials).all { AccessPolicy.allows(actor.profile, it) }) {
            "No puedes asignar ni denegar capacidades fuera de tu alcance."
        }
        val profile = AccessProfile(role, grants, denials)
        require(AccessPolicy.effectivePermissions(profile).all {
            AccessPolicy.allows(actor.profile, it)
        }) { "El perfil resultante no puede superar tus propios permisos." }
        val encodedGrants = grants.sortedBy { it.name }.joinToString("|") { it.name }
        val encodedDenials = denials.sortedBy { it.name }.joinToString("|") { it.name }
        if (target.grants != encodedGrants || target.denials != encodedDenials) {
            persistStaffChange {
                staffAccounts.updatePermissions(staffId, encodedGrants, encodedDenials)
                audit(AuditLogEntity(
                    actorStaffId = actor.staffId,
                    actorUsername = actor.username,
                    action = "STAFF_PERMISSIONS_UPDATED",
                    targetType = "STAFF_ACCOUNT",
                    targetId = staffId,
                    details = "username=${target.username};role=${target.role};oldGrants=${target.grants};oldDenials=${target.denials};newGrants=$encodedGrants;newDenials=$encodedDenials"
                ))
            }
        }
    }

    suspend fun isSessionValid(session: StaffSession): Boolean {
        val account = staffAccounts.findById(session.staffId) ?: return false
        if (!account.isActive || account.username != session.username) return false
        val role = runCatching { StaffRole.valueOf(account.role) }.getOrNull() ?: return false
        return role == session.profile.role &&
            parsePermissions(account.grants) == session.profile.grants &&
            parsePermissions(account.denials) == session.profile.denials
    }

    /**
     * Revalidates the persisted account and its current permission snapshot for
     * every sensitive action. UI visibility is only a convenience, never the
     * authorization check used by an operation handler.
     */
    suspend fun hasPermission(session: StaffSession, permission: Permission): Boolean {
        if (!isSessionValid(session)) return false
        return AccessPolicy.allows(session.profile, permission)
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
                ?: run {
                    audit(AuditLogEntity(actorStaffId = null, actorUsername = normalizedUsername.take(64).ifBlank { "unknown" }, action = "LOGIN_FAILED_UNKNOWN_USER", targetType = "AUTHENTICATION", targetId = null, details = "reason=unknown_username"))
                    return AuthenticationResult.INVALID_CREDENTIALS to null
                }

            val now = clock()
            if (account.lockedUntil?.let { it > now } == true) {
                audit(AuditLogEntity(actorStaffId = account.id, actorUsername = account.username, action = "LOGIN_BLOCKED_LOCKOUT", targetType = "AUTHENTICATION", targetId = account.id, details = "reason=account_locked"))
                return AuthenticationResult.ACCOUNT_LOCKED to null
            }

            val storedHash = PasswordHash(
                algorithm = account.passwordAlgorithm,
                iterations = account.passwordIterations,
                saltBase64 = account.passwordSaltBase64,
                hashBase64 = account.passwordHashBase64
            )
            if (!PasswordHasher.verify(password, storedHash)) {
                staffAccounts.recordFailedLogin(
                    id = account.id,
                    now = now,
                    maxAttempts = MAX_FAILED_LOGIN_ATTEMPTS,
                    lockUntil = Math.addExact(now, LOGIN_LOCKOUT_MILLIS)
                )
                audit(AuditLogEntity(actorStaffId = account.id, actorUsername = account.username, action = "LOGIN_FAILED_PASSWORD", targetType = "AUTHENTICATION", targetId = account.id, details = "attempts=\${account.failedLoginAttempts + 1};lockTriggered=\${account.failedLoginAttempts + 1 >= MAX_FAILED_LOGIN_ATTEMPTS}"))
                return AuthenticationResult.INVALID_CREDENTIALS to null
            }
            if (!account.isActive) {
                audit(AuditLogEntity(actorStaffId = account.id, actorUsername = account.username, action = "LOGIN_BLOCKED_DISABLED", targetType = "AUTHENTICATION", targetId = account.id, details = "reason=account_disabled"))
                return AuthenticationResult.ACCOUNT_DISABLED to null
            }

            val role = runCatching { StaffRole.valueOf(account.role) }.getOrNull()
                ?: return AuthenticationResult.INVALID_CREDENTIALS to null
            val profile = AccessProfile(
                role = role,
                grants = parsePermissions(account.grants),
                denials = parsePermissions(account.denials)
            )
            staffAccounts.resetFailedLogins(account.id)
            staffAccounts.updateLastLogin(account.id, now)
            audit(AuditLogEntity(actorStaffId = account.id, actorUsername = account.username, action = "LOGIN_SUCCESS", targetType = "AUTHENTICATION", targetId = account.id, details = "role=\${role.name}"))
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

    private companion object {
        const val MAX_FAILED_LOGIN_ATTEMPTS = 5
        const val LOGIN_LOCKOUT_MILLIS = 15 * 60 * 1000L
    }

    private fun normalizeUsername(username: String): String =
        username.trim().lowercase(Locale.ROOT)

    private fun parsePermissions(encoded: String): Set<Permission> =
        encoded.split('|').mapNotNull { token ->
            runCatching { Permission.valueOf(token) }.getOrNull()
        }.toSet()
}
