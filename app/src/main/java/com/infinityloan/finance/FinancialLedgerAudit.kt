package com.infinityloan.finance

/** Read-only ledger inputs for auditing principal, installment status and payment allocations. */
data class LedgerLoan(val id:String,val principalMinor:Long,val balanceMinor:Long)
data class LedgerInstallment(val loanId:String,val number:Int,val principalMinor:Long,val interestMinor:Long,val totalMinor:Long,val paidPrincipalMinor:Long,val paidInterestMinor:Long,val status:String)
data class LedgerPayment(val operationId:String,val loanId:String,val amountMinor:Long)
data class LedgerAllocation(val operationId:String,val loanId:String,val installmentNumber:Int,val lateFeeMinor:Long,val chargesMinor:Long,val interestMinor:Long,val principalMinor:Long)
data class LedgerCredit(val operationId:String,val loanId:String,val amountMinor:Long)
enum class LedgerIssueType { SCHEDULE_PRINCIPAL_MISMATCH, INSTALLMENT_TOTAL_MISMATCH, INVALID_PAID_AMOUNT, INSTALLMENT_STATUS_MISMATCH, LOAN_BALANCE_MISMATCH, PAYMENT_WITHOUT_ALLOCATION, ALLOCATION_EXCEEDS_PAYMENT, ALLOCATION_INSTALLMENT_MISSING, ALLOCATION_SUM_MISMATCH, NEGATIVE_ALLOCATION, PAYMENT_LOAN_MISMATCH, DUPLICATE_ALLOCATION }
data class LedgerIssue(val type:LedgerIssueType,val key:String,val details:String)

/** Diagnostic only: never modifies financial records. */
object FinancialLedgerAudit {
 fun audit(loans:List<LedgerLoan>,installments:List<LedgerInstallment>,payments:List<LedgerPayment>,allocations:List<LedgerAllocation>,excessCredits:List<LedgerCredit>,reversedIds:Set<String> = emptySet()):List<LedgerIssue>{
  val issues=mutableListOf<LedgerIssue>()
  loans.sortedBy{it.id}.forEach { loan ->
   val rows=installments.filter{it.loanId==loan.id}
   if(rows.isNotEmpty() && rows.fold(0L){a,r->Math.addExact(a,r.principalMinor)}!=loan.principalMinor)
    issues+=LedgerIssue(LedgerIssueType.SCHEDULE_PRINCIPAL_MISMATCH,loan.id,"La suma del capital programado difiere del capital original.")
   rows.sortedBy{it.number}.forEach { row ->
    if(row.totalMinor!=Math.addExact(row.principalMinor,row.interestMinor))
     issues+=LedgerIssue(LedgerIssueType.INSTALLMENT_TOTAL_MISMATCH,"${loan.id}:${row.number}","El total no equivale a capital más interés.")
    if(row.paidPrincipalMinor<0||row.paidInterestMinor<0||row.paidPrincipalMinor>row.principalMinor||row.paidInterestMinor>row.interestMinor)
     issues+=LedgerIssue(LedgerIssueType.INVALID_PAID_AMOUNT,"${loan.id}:${row.number}","Importes pagados fuera de límites contractuales.")
    val fully=row.paidPrincipalMinor==row.principalMinor&&row.paidInterestMinor==row.interestMinor
    if(row.status.equals("PAID",true)!=fully)
     issues+=LedgerIssue(LedgerIssueType.INSTALLMENT_STATUS_MISMATCH,"${loan.id}:${row.number}","Estado ${row.status} no coincide con importes pagados.")
   }
   val paid=rows.fold(0L){a,r->Math.addExact(a,r.paidPrincipalMinor)}
   val expected=Math.subtractExact(loan.principalMinor,paid)
   if(expected<0||expected!=loan.balanceMinor)
    issues+=LedgerIssue(LedgerIssueType.LOAN_BALANCE_MISMATCH,loan.id,"Saldo guardado ${loan.balanceMinor} difiere del calculado ${expected}.")
  }
  payments.filter{it.operationId !in reversedIds}.sortedBy{it.operationId}.forEach{p ->
   val rows=allocations.filter{it.operationId==p.operationId}
   val allocated=rows.fold(0L){a,r->Math.addExact(a,Math.addExact(Math.addExact(r.lateFeeMinor,r.chargesMinor),Math.addExact(r.interestMinor,r.principalMinor)))}
   val credit=excessCredits.filter{it.operationId=="PAYMENT-EXCESS-${p.operationId}"}.fold(0L){a,r->Math.addExact(a,r.amountMinor)}
   if(rows.isEmpty()&&p.amountMinor>0&&credit==0L) issues+=LedgerIssue(LedgerIssueType.PAYMENT_WITHOUT_ALLOCATION,p.operationId,"Pago sin distribución ni crédito excedente identificado.")
   if(allocated>p.amountMinor) issues+=LedgerIssue(LedgerIssueType.ALLOCATION_EXCEEDS_PAYMENT,p.operationId,"La distribución supera el importe del pago.")
   if(Math.addExact(allocated,credit)!=p.amountMinor) issues+=LedgerIssue(LedgerIssueType.ALLOCATION_SUM_MISMATCH,p.operationId,"Distribución más crédito excedente no coincide con el pago.")
   rows.forEach{a->
   if(installments.none{it.loanId==a.loanId&&it.number==a.installmentNumber}) issues+=LedgerIssue(LedgerIssueType.ALLOCATION_INSTALLMENT_MISSING,p.operationId,"Asignación apunta a cuota inexistente ${a.loanId}:${a.installmentNumber}.")
   if(a.loanId!=p.loanId) issues+=LedgerIssue(LedgerIssueType.PAYMENT_LOAN_MISMATCH,p.operationId,"La distribución está vinculada a otro préstamo.")
   if(listOf(a.lateFeeMinor,a.chargesMinor,a.interestMinor,a.principalMinor).any{it<0L}) issues+=LedgerIssue(LedgerIssueType.NEGATIVE_ALLOCATION,p.operationId,"La distribución contiene importes negativos.")
  }
  if(rows.groupBy{it.loanId to it.installmentNumber}.any{it.value.size>1}) issues+=LedgerIssue(LedgerIssueType.DUPLICATE_ALLOCATION,p.operationId,"Hay distribuciones duplicadas para una misma cuota.")
  }
  return issues.distinct().sortedWith(compareBy<LedgerIssue>{it.type.name}.thenBy{it.key})
 }
}
