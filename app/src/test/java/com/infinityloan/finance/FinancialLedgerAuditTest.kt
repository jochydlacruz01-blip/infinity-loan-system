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

 @Test fun detectsPendingStatusWhenInstallmentHasPartialPayment(){
  val findings=FinancialLedgerAudit.audit(
   listOf(LedgerLoan("L1",1000,900)),
   listOf(LedgerInstallment("L1",1,1000,100,1100,100,0,"PENDING")),
   emptyList(),emptyList(),emptyList()
  )
  assertTrue(findings.any{it.type==LedgerIssueType.INSTALLMENT_STATUS_MISMATCH})
 }
 @Test fun detectsPartialStatusWhenInstallmentIsUnpaid(){
  val findings=FinancialLedgerAudit.audit(
   listOf(LedgerLoan("L1",1000,1000)),
   listOf(LedgerInstallment("L1",1,1000,100,1100,0,0,"PARTIAL")),
   emptyList(),emptyList(),emptyList()
  )
  assertTrue(findings.any{it.type==LedgerIssueType.INSTALLMENT_STATUS_MISMATCH})
 }
 @Test fun feeOnlyPaymentRequiresPartialStatus(){
  val findings=FinancialLedgerAudit.audit(
   listOf(LedgerLoan("L1",1000,1000)),
   listOf(LedgerInstallment("L1",1,1000,100,1100,0,0,"PENDING",paidLateFeeMinor=25)),
   emptyList(),emptyList(),emptyList()
  )
  assertTrue(findings.any{it.type==LedgerIssueType.INSTALLMENT_STATUS_MISMATCH})
 }
 @Test fun feeOnlyPaymentWithPartialStatusIsConsistent(){
  val findings=FinancialLedgerAudit.audit(
   listOf(LedgerLoan("L1",1000,1000)),
   listOf(LedgerInstallment("L1",1,1000,100,1100,0,0,"PARTIAL",paidChargesMinor=15)),
   emptyList(),emptyList(),emptyList()
  )
  assertTrue(findings.none{it.type==LedgerIssueType.INSTALLMENT_STATUS_MISMATCH})
 } 
 @Test fun detectsDuplicatePaymentsMissingLoanAndNegativeCredit(){
  val findings=FinancialLedgerAudit.audit(
   emptyList(),emptyList(),
   listOf(LedgerPayment("DUP","NO-LOAN",100),LedgerPayment("DUP","NO-LOAN",100)),
   emptyList(),listOf(LedgerCredit("PAYMENT-EXCESS-DUP","NO-LOAN",-1))
  )
  assertTrue(findings.any{it.type==LedgerIssueType.DUPLICATE_PAYMENT})
  assertTrue(findings.any{it.type==LedgerIssueType.PAYMENT_LOAN_NOT_FOUND})
  assertTrue(findings.any{it.type==LedgerIssueType.INVALID_CREDIT_AMOUNT})
 }

 @Test fun flagsNegativePaymentsAndPaidFees(){
  val findings=FinancialLedgerAudit.audit(
   listOf(LedgerLoan("L1",1000,1000)),
   listOf(LedgerInstallment("L1",1,1000,100,1100,0,0,"PENDING",paidLateFeeMinor=-1)),
   listOf(LedgerPayment("P-NEG","L1",-5)),emptyList(),emptyList()
  )
  assertTrue(findings.any{it.type==LedgerIssueType.INVALID_PAYMENT_AMOUNT})
  assertTrue(findings.any{it.type==LedgerIssueType.INVALID_PAID_FEES})
 }
 @Test fun flagsNegativeContractualInstallmentAmounts(){
  val findings=FinancialLedgerAudit.audit(
   listOf(LedgerLoan("L1",0,0)),
   listOf(LedgerInstallment("L1",1,-10,0,-10,0,0,"PENDING")),
   emptyList(),emptyList(),emptyList()
  )
  assertTrue(findings.any{it.type==LedgerIssueType.INSTALLMENT_TOTAL_MISMATCH})
 }

 @Test fun auditReportsOverflowInsteadOfCrashing(){
  val findings=FinancialLedgerAudit.audit(
   listOf(LedgerLoan("L1",Long.MAX_VALUE,Long.MAX_VALUE)),
   listOf(LedgerInstallment("L1",1,Long.MAX_VALUE,0,Long.MAX_VALUE,0,0,"PENDING"),LedgerInstallment("L1",2,1,0,1,0,0,"PENDING")),
   emptyList(),emptyList(),emptyList()
  )
  assertTrue(findings.any{it.type==LedgerIssueType.ARITHMETIC_OVERFLOW})
 }
 @Test fun detectsAllocationWithoutPaymentAndIgnoresReversedOrphan(){
  val orphan=LedgerAllocation("ORPHAN","L1",1,0,0,0,100)
  val findings=FinancialLedgerAudit.audit(listOf(LedgerLoan("L1",1000,1000)),listOf(LedgerInstallment("L1",1,1000,0,1000,0,0,"PENDING")),emptyList(),listOf(orphan),emptyList())
  assertTrue(findings.any{it.type==LedgerIssueType.ORPHAN_ALLOCATION})
  val reversed=FinancialLedgerAudit.audit(listOf(LedgerLoan("L1",1000,1000)),listOf(LedgerInstallment("L1",1,1000,0,1000,0,0,"PENDING")),emptyList(),listOf(orphan),emptyList(),setOf("ORPHAN"))
  assertTrue(reversed.none{it.type==LedgerIssueType.ORPHAN_ALLOCATION})
 }
 @Test fun activeAllocationTotalsMustMatchInstallmentPaidCounters(){
  val findings=FinancialLedgerAudit.audit(
   listOf(LedgerLoan("L1",1000,900)),
   listOf(LedgerInstallment("L1",1,1000,100,1100,100,0,"PARTIAL")),
   listOf(LedgerPayment("P1","L1",100)),
   listOf(LedgerAllocation("P1","L1",1,0,0,0,50)),emptyList()
  )
  assertTrue(findings.any{it.type==LedgerIssueType.INSTALLMENT_ALLOCATION_LEDGER_MISMATCH})
 }
}
