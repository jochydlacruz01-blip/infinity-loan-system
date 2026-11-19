package com.infinityloan.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessPolicyTest {

    @Test
    fun administratorReceivesAllPermissionsByDefault() {
        assertTrue(
            AccessPolicy.effectivePermissions(
                AccessProfile(StaffRole.ADMINISTRATOR)
            ).containsAll(Permission.entries)
        )
    }

    @Test
    fun cashierCanRegisterPaymentsButCannotManageStaffOrSystemSettings() {
        val profile = AccessProfile(StaffRole.CASHIER)

        assertTrue(AccessPolicy.allows(profile, Permission.PAYMENT_REGISTER))
        assertTrue(AccessPolicy.allows(profile, Permission.CASH_SESSION_CLOSE))
        assertFalse(AccessPolicy.allows(profile, Permission.STAFF_MANAGE))
        assertFalse(AccessPolicy.allows(profile, Permission.SYSTEM_SETTINGS_MANAGE))
    }

    @Test
    fun collectorCannotCreateLoansOrManageCashSessionsByDefault() {
        val profile = AccessProfile(StaffRole.COLLECTOR)

        assertTrue(AccessPolicy.allows(profile, Permission.CLIENT_VIEW))
        assertTrue(AccessPolicy.allows(profile, Permission.PAYMENT_REGISTER))
        assertFalse(AccessPolicy.allows(profile, Permission.LOAN_CREATE))
        assertFalse(AccessPolicy.allows(profile, Permission.CASH_SESSION_OPEN))
    }

    @Test
    fun explicitDenialOverridesRoleDefaultAndGrant() {
        val profile = AccessProfile(
            role = StaffRole.ADMINISTRATOR,
            grants = setOf(Permission.PAYMENT_REGISTER),
            denials = setOf(Permission.PAYMENT_REGISTER)
        )

        assertFalse(AccessPolicy.allows(profile, Permission.PAYMENT_REGISTER))
    }

    @Test
    fun customGrantAddsPermissionWithoutChangingRoleDefaults() {
        val profile = AccessProfile(
            role = StaffRole.COLLECTOR,
            grants = setOf(Permission.REPORT_VIEW)
        )

        assertTrue(AccessPolicy.allows(profile, Permission.REPORT_VIEW))
        assertFalse(AccessPolicy.allows(profile, Permission.REPORT_EXPORT))
        assertFalse(AccessPolicy.allows(profile, Permission.LOAN_CREATE))
    }

    @Test
    fun effectivePermissionsExcludeDeniedCapabilities() {
        val profile = AccessProfile(
            role = StaffRole.SUPERVISOR,
            denials = setOf(Permission.REPORT_EXPORT, Permission.STAFF_VIEW)
        )
        val effective = AccessPolicy.effectivePermissions(profile)

        assertTrue(Permission.PAYMENT_REGISTER in effective)
        assertFalse(Permission.REPORT_EXPORT in effective)
        assertFalse(Permission.STAFF_VIEW in effective)
    }
}
