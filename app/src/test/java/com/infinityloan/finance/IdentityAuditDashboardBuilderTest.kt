package com.infinityloan.finance

import org.junit.Assert.*
import org.junit.Test

class IdentityAuditDashboardBuilderTest {
    @Test fun groupsAndSortsFindingsDeterministically() {
        val findings=listOf(
            IdentityIntegrityIssue(IdentityIntegrityIssueType.PAYMENT_LOAN_MISSING,"p2","missing loan"),
            IdentityIntegrityIssue(IdentityIntegrityIssueType.LOAN_CLIENT_MISSING,"l1","missing client"),
            IdentityIntegrityIssue(IdentityIntegrityIssueType.PAYMENT_LOAN_MISSING,"p1","missing loan")
        )
        val result=IdentityAuditDashboardBuilder.build(findings)
        assertEquals(3,result.totalFindings)
        assertEquals(2,result.categories.first().count)
        assertEquals(listOf("l1","p1","p2"),result.affectedKeys)
        assertFalse(result.clean)
        assertEquals(result,IdentityAuditDashboardBuilder.build(findings.reversed()))
    }
    @Test fun emptyFindingsMeansCleanSummary() {
        val result=IdentityAuditDashboardBuilder.build(emptyList())
        assertTrue(result.clean)
        assertEquals(0,result.totalFindings)
        assertTrue(result.categories.isEmpty())
        assertTrue(result.affectedKeys.isEmpty())
    }
}
