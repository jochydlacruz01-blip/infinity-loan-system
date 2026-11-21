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
    fun auditHistoryIsAvailableByDefaultOnlyToAdministratorAndSupervisor() {
        assertTrue(AccessPolicy.allows(AccessProfile(StaffRole.ADMINISTRATOR), Permission.AUDIT_LOG_VIEW))
        assertTrue(AccessPolicy.allows(AccessProfile(StaffRole.SUPERVISOR), Permission.AUDIT_LOG_VIEW))
        assertFalse(AccessPolicy.allows(AccessProfile(StaffRole.CASHIER), Permission.AUDIT_LOG_VIEW))
        assertFalse(AccessPolicy.allows(AccessProfile(StaffRole.COLLECTOR), Permission.AUDIT_LOG_VIEW))
    }

    @Test
    fun auditPermissionCanBeExplicitlyDeniedOrGranted() {
        assertFalse(AccessPolicy.allows(AccessProfile(StaffRole.ADMINISTRATOR, denials = setOf(Permission.AUDIT_LOG_VIEW)), Permission.AUDIT_LOG_VIEW))
        assertTrue(AccessPolicy.allows(AccessProfile(StaffRole.COLLECTOR, grants = setOf(Permission.AUDIT_LOG_VIEW)), Permission.AUDIT_LOG_VIEW))
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
    fun onlyAdministratorHasPaymentReversalByDefault() {
        assertTrue(AccessPolicy.allows(AccessProfile(StaffRole.ADMINISTRATOR), Permission.PAYMENT_REVERSE))
        assertFalse(AccessPolicy.allows(AccessProfile(StaffRole.SUPERVISOR), Permission.PAYMENT_REVERSE))
        assertFalse(AccessPolicy.allows(AccessProfile(StaffRole.CASHIER), Permission.PAYMENT_REVERSE))
        assertFalse(AccessPolicy.allows(AccessProfile(StaffRole.COLLECTOR), Permission.PAYMENT_REVERSE))
    }

    @Test
    fun paymentReversalPermissionCanBeExplicitlyGrantedOrDenied() {
        val granted = AccessProfile(StaffRole.CASHIER, grants = setOf(Permission.PAYMENT_REVERSE))
        val denied = AccessProfile(StaffRole.ADMINISTRATOR, denials = setOf(Permission.PAYMENT_REVERSE))
        assertTrue(AccessPolicy.allows(granted, Permission.PAYMENT_REVERSE))
        assertFalse(AccessPolicy.allows(denied, Permission.PAYMENT_REVERSE))
    }

    @Test
    fun collectionVisitPermissionsMatchOperationalRoles() {
        assertTrue(AccessPolicy.allows(AccessProfile(StaffRole.ADMINISTRATOR), Permission.COLLECTION_VISIT_MANAGE))
        assertTrue(AccessPolicy.allows(AccessProfile(StaffRole.SUPERVISOR), Permission.COLLECTION_VISIT_MANAGE))
        assertTrue(AccessPolicy.allows(AccessProfile(StaffRole.COLLECTOR), Permission.COLLECTION_VISIT_MANAGE))
        assertTrue(AccessPolicy.allows(AccessProfile(StaffRole.CASHIER), Permission.COLLECTION_VISIT_VIEW))
        assertFalse(AccessPolicy.allows(AccessProfile(StaffRole.CASHIER), Permission.COLLECTION_VISIT_MANAGE))
    }

    @Test
    fun collectorCannotManageCashMovementsButCashierCan() {
        assertFalse(AccessPolicy.allows(AccessProfile(StaffRole.COLLECTOR), Permission.CASH_MOVEMENT_MANAGE))
        assertTrue(AccessPolicy.allows(AccessProfile(StaffRole.CASHIER), Permission.CASH_MOVEMENT_MANAGE))
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
    @Test
    fun supervisorCanReviewCashVarianceButCashierAndCollectorCannotByDefault() {
        assertTrue(
            AccessPolicy.allows(
                AccessProfile(StaffRole.SUPERVISOR),
                Permission.CASH_VARIANCE_REVIEW
            )
        )
        assertFalse(
            AccessPolicy.allows(
                AccessProfile(StaffRole.CASHIER),
                Permission.CASH_VARIANCE_REVIEW
            )
        )
        assertFalse(
            AccessPolicy.allows(
                AccessProfile(StaffRole.COLLECTOR),
                Permission.CASH_VARIANCE_REVIEW
            )
        )
    }

    @Test
    fun cashVarianceReviewPermissionCanBeExplicitlyGrantedOrDenied() {
        val granted = AccessProfile(
            StaffRole.CASHIER,
            grants = setOf(Permission.CASH_VARIANCE_REVIEW)
        )
        val denied = AccessProfile(
            StaffRole.SUPERVISOR,
            denials = setOf(Permission.CASH_VARIANCE_REVIEW)
        )

        assertTrue(AccessPolicy.allows(granted, Permission.CASH_VARIANCE_REVIEW))
        assertFalse(AccessPolicy.allows(denied, Permission.CASH_VARIANCE_REVIEW))
    }

}
