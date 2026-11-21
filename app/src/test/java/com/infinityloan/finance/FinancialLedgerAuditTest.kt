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
 @Test fun healthyLedgerProducesNoFindings(){
  val findings=FinancialLedgerAudit.audit(listOf(LedgerLoan("L1",10000,9000)),listOf(LedgerInstallment("L1",1,10000,500,10500,1000,0,"PARTIAL")),listOf(LedgerPayment("P1","L1",1000)),listOf(LedgerAllocation("P1","L1",1,0,0,0,1000)),emptyList())
  assertTrue(findings.isEmpty())
 }
}
