package com.infinityloan.finance

import com.infinityloan.security.StaffRole
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CollectionVisitAssignmentPolicyTest {
    @Test fun administratorAndSupervisorCanAssignActiveCollectors() {
        for (role in listOf(StaffRole.ADMINISTRATOR, StaffRole.SUPERVISOR)) {
            assertEquals(
                "collector-2",
                CollectionVisitAssignmentPolicy.resolve(
                    role, "manager-1", "collector-2", StaffRole.COLLECTOR.name, true
                )
            )
        }
    }

    @Test fun collectorCanOnlyAssignSelf() {
        assertEquals(
            "collector-1",
            CollectionVisitAssignmentPolicy.resolve(
                StaffRole.COLLECTOR, "collector-1", "collector-1", null, false
            )
        )
        expectRejected {
            CollectionVisitAssignmentPolicy.resolve(
                StaffRole.COLLECTOR, "collector-1", "collector-2", StaffRole.COLLECTOR.name, true
            )
        }
    }

    @Test fun inactiveOrNonCollectorTargetsAreRejected() {
        expectRejected {
            CollectionVisitAssignmentPolicy.resolve(
                StaffRole.SUPERVISOR, "manager-1", "collector-2", StaffRole.COLLECTOR.name, false
            )
        }
        expectRejected {
            CollectionVisitAssignmentPolicy.resolve(
                StaffRole.ADMINISTRATOR, "admin-1", "cashier-1", StaffRole.CASHIER.name, true
            )
        }
        expectRejected {
            CollectionVisitAssignmentPolicy.resolve(
                StaffRole.SUPERVISOR, "manager-1", "missing", null, false
            )
        }
    }

    @Test fun rolesWithoutAssignmentAuthorityAreRejected() {
        expectRejected {
            CollectionVisitAssignmentPolicy.resolve(
                StaffRole.CASHIER, "cashier-1", "collector-2", StaffRole.COLLECTOR.name, true
            )
        }
    }

    private fun expectRejected(block: () -> Unit) {
        try {
            block()
            fail("Expected assignment to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected authorization rejection.
        }
    }
}
