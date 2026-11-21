package com.infinityloan.finance
import org.junit.Assert.assertTrue
import org.junit.Test
class FinancialLedgerAuditTest {
 @Test fun detectsStatusBalanceAndMissingAllocation(){
  val findings=FinancialLedgerAudit.audit(listOf(LedgerLoan("L1",10000,9000)),listOf(LedgerInstallment("L1",1,10000,500,10500,0,0,"PAID")),listOf(LedgerPayment("P1","L1",1000)),emptyList(),emptyList())
  assertTrue(findings.any{it.type==LedgerIssueType.INSTALLMENT_STATUS_MISMATCH})
  assertTrue(findings.any{it.type==LedgerIssueType.LOAN_BALANCE_MISMATCH})
  assertTrue(findings.any{it.type==LedgerIssueType.PAYMENT_WITHOUT_ALLOCATION})
 }
 @Test fun detectsWrongLoanNegativeAndDuplicateAllocations(){
  val allocations=listOf(
   LedgerAllocation("P1","L2",1,-1,0,0,100),
   LedgerAllocation("P1","L2",1,0,0,0,100)
  )
  val findings=FinancialLedgerAudit.audit(
   listOf(LedgerLoan("L1",1000,900)),
   listOf(LedgerInstallment("L1",1,1000,0,1000,100,0,"PARTIAL")),
   listOf(LedgerPayment("P1","L1",200)),allocations,emptyList()
  )
  assertTrue(findings.any{it.type==LedgerIssueType.PAYMENT_LOAN_MISMATCH})
  assertTrue(findings.any{it.type==LedgerIssueType.NEGATIVE_ALLOCATION})
  assertTrue(findings.any{it.type==LedgerIssueType.DUPLICATE_ALLOCATION})
 }
 @Test fun healthyLedgerProducesNoFindings(){
  val findings=FinancialLedgerAudit.audit(listOf(LedgerLoan("L1",10000,9000)),listOf(LedgerInstallment("L1",1,10000,500,10500,1000,0,"PARTIAL")),listOf(LedgerPayment("P1","L1",1000)),listOf(LedgerAllocation("P1","L1",1,0,0,0,1000)),emptyList())
  assertTrue(findings.isEmpty())
 }
 @Test fun reversedPaymentIsExcludedFromActiveLedgerAllocationChecks(){
  val findings=FinancialLedgerAudit.audit(
   listOf(LedgerLoan("L1",1000,1000)),
   listOf(LedgerInstallment("L1",1,1000,0,1000,0,0,"PENDING")),
   listOf(LedgerPayment("P-REV","L1",500)),
   emptyList(),emptyList(),setOf("P-REV")
  )
  assertTrue(findings.none{it.type==LedgerIssueType.PAYMENT_WITHOUT_ALLOCATION})
  assertTrue(findings.none{it.type==LedgerIssueType.ALLOCATION_SUM_MISMATCH})
 }

}
