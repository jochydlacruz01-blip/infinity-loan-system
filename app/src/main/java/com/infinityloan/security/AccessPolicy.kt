package com.infinityloan.security

/**
 * Permissions are application capabilities, not screen names.
 * Every sensitive operation must check this policy in its domain/use-case layer;
 * hiding a button is not an authorization boundary.
 */
enum class StaffRole {
    ADMINISTRATOR,
    SUPERVISOR,
    CASHIER,
    COLLECTOR
}

enum class Permission {
    DASHBOARD_VIEW,
    CLIENT_VIEW,
    CLIENT_CREATE,
    CLIENT_UPDATE,
    LOAN_VIEW,
    LOAN_CREATE,
    LOAN_UPDATE,
    PAYMENT_VIEW,
    PAYMENT_REGISTER,
    PAYMENT_REVERSE,
    CUSTOMER_CREDIT_VIEW,
    CUSTOMER_CREDIT_APPLY,
    PROMISE_TO_PAY_VIEW,
    PROMISE_TO_PAY_CREATE,
    PROMISE_TO_PAY_RESOLVE,
    CASH_SESSION_OPEN,
    CASH_SESSION_CLOSE,
    CASH_MOVEMENT_MANAGE,
    REPORT_VIEW,
    REPORT_EXPORT,
    STAFF_VIEW,
    STAFF_MANAGE,
    ROLE_PERMISSION_MANAGE,
    SYSTEM_SETTINGS_MANAGE,
    AUDIT_LOG_VIEW,
    CASH_VARIANCE_REVIEW
}

data class AccessProfile(
    val role: StaffRole,
    val grants: Set<Permission> = emptySet(),
    val denials: Set<Permission> = emptySet()
)

object AccessPolicy {
    private val administrator = Permission.entries.toSet()

    private val supervisor = setOf(
        Permission.DASHBOARD_VIEW, Permission.CLIENT_VIEW, Permission.CLIENT_CREATE,
        Permission.CLIENT_UPDATE, Permission.LOAN_VIEW, Permission.LOAN_CREATE,
        Permission.LOAN_UPDATE, Permission.PAYMENT_VIEW, Permission.PAYMENT_REGISTER,
        Permission.CUSTOMER_CREDIT_VIEW, Permission.PROMISE_TO_PAY_VIEW,
        Permission.PROMISE_TO_PAY_CREATE, Permission.PROMISE_TO_PAY_RESOLVE,
        Permission.CASH_SESSION_OPEN, Permission.CASH_SESSION_CLOSE,
        Permission.CASH_MOVEMENT_MANAGE, Permission.REPORT_VIEW, Permission.REPORT_EXPORT,
        Permission.STAFF_VIEW, Permission.AUDIT_LOG_VIEW, Permission.CASH_VARIANCE_REVIEW
    )

    private val cashier = setOf(
        Permission.DASHBOARD_VIEW, Permission.CLIENT_VIEW, Permission.LOAN_VIEW,
        Permission.PAYMENT_VIEW, Permission.PAYMENT_REGISTER,
        Permission.CUSTOMER_CREDIT_VIEW, Permission.CUSTOMER_CREDIT_APPLY,
        Permission.PROMISE_TO_PAY_VIEW, Permission.PROMISE_TO_PAY_CREATE,
        Permission.CASH_SESSION_OPEN, Permission.CASH_SESSION_CLOSE,
        Permission.CASH_MOVEMENT_MANAGE
    )

    private val collector = setOf(
        Permission.DASHBOARD_VIEW, Permission.CLIENT_VIEW, Permission.LOAN_VIEW,
        Permission.PAYMENT_VIEW, Permission.PAYMENT_REGISTER,
        Permission.PROMISE_TO_PAY_VIEW, Permission.PROMISE_TO_PAY_CREATE
    )

    fun defaultPermissions(role: StaffRole): Set<Permission> = when (role) {
        StaffRole.ADMINISTRATOR -> administrator
        StaffRole.SUPERVISOR -> supervisor
        StaffRole.CASHIER -> cashier
        StaffRole.COLLECTOR -> collector
    }

    fun allows(profile: AccessProfile, permission: Permission): Boolean {
        if (permission in profile.denials) return false
        if (permission in profile.grants) return true
        return permission in defaultPermissions(profile.role)
    }

    fun effectivePermissions(profile: AccessProfile): Set<Permission> =
        Permission.entries.filterTo(mutableSetOf()) { allows(profile, it) }
}
