package com.infinityloan.finance

import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialIdentityIntegrityTest {
    @Test fun cleanIdentityChainProducesNoFindings() {
        assertEquals(emptyList<IdentityIntegrityIssue>(), FinancialIdentityIntegrity.audit(
            listOf(IdentityClient("c1")), listOf(IdentityLoan("l1","c1","DOP")),
            listOf(IdentityPayment("p1","l1","DOP")), listOf(IdentityAllocation("p1","l1",1))
        ))
    }
    @Test fun detectsMissingClientLoanAndAllocationMismatch() {
        val issues=FinancialIdentityIntegrity.audit(
            listOf(IdentityClient("c1")), listOf(IdentityLoan("l1","missing","DOP"),IdentityLoan("l2","c1","USD")),
            listOf(IdentityPayment("p1","l2","DOP")), listOf(IdentityAllocation("p1","l1",1),IdentityAllocation("lost","l1",2))
        )
        assertEquals(1,issues.count{it.type==IdentityIntegrityIssueType.LOAN_CLIENT_MISSING})
        assertEquals(1,issues.count{it.type==IdentityIntegrityIssueType.PAYMENT_CURRENCY_MISMATCH})
        assertEquals(1,issues.count{it.type==IdentityIntegrityIssueType.ALLOCATION_LOAN_MISMATCH})
        assertEquals(1,issues.count{it.type==IdentityIntegrityIssueType.ALLOCATION_PAYMENT_MISSING})
    }
    @Test fun detectsDuplicateAllocationsAndInvalidInstallmentNumbers() {
        val issues=FinancialIdentityIntegrity.audit(
            listOf(IdentityClient("c")), listOf(IdentityLoan("l","c","DOP")),
            listOf(IdentityPayment("p","l","DOP")),
            listOf(IdentityAllocation("p","l",1),IdentityAllocation("p","l",1),IdentityAllocation("p","l",0))
        )
        assertEquals(1,issues.count{it.type==IdentityIntegrityIssueType.DUPLICATE_ALLOCATION})
        assertEquals(1,issues.count{it.type==IdentityIntegrityIssueType.INVALID_INSTALLMENT_NUMBER})
    }
    @Test fun deterministicAndDetectsDuplicatePaymentIds() {
        val payments=listOf(IdentityPayment("p","l","DOP"),IdentityPayment("p","missing","DOP"))
        val inputs=listOf(IdentityClient("c"),IdentityClient("other"))
        val loans=listOf(IdentityLoan("l","c","DOP"))
        val a=FinancialIdentityIntegrity.audit(inputs,loans,payments,emptyList())
        val b=FinancialIdentityIntegrity.audit(inputs.reversed(),loans.reversed(),payments.reversed(),emptyList())
        assertEquals(a,b)
        assertEquals(1,a.count{it.type==IdentityIntegrityIssueType.DUPLICATE_PAYMENT_ID})
    }
}
