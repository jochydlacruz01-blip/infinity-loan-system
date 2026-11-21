package com.infinityloan.finance

/** Diagnostic-only cross-check of the identity chain client -> loan -> payment -> allocations. */
data class IdentityClient(val id: String)
data class IdentityLoan(val id: String, val clientId: String, val currency: String)
data class IdentityPayment(val operationId: String, val loanId: String, val currency: String)
data class IdentityAllocation(val operationId: String, val loanId: String, val installmentNumber: Int)

enum class IdentityIntegrityIssueType {
    LOAN_CLIENT_MISSING,
    PAYMENT_LOAN_MISSING,
    PAYMENT_CURRENCY_MISMATCH,
    ALLOCATION_PAYMENT_MISSING,
    ALLOCATION_LOAN_MISMATCH,
    DUPLICATE_PAYMENT_ID
}
data class IdentityIntegrityIssue(
    val type: IdentityIntegrityIssueType,
    val key: String,
    val details: String
)

/** Pure audit; never mutates or reassigns financial records. */
object FinancialIdentityIntegrity {
    fun audit(
        clients: List<IdentityClient>,
        loans: List<IdentityLoan>,
        payments: List<IdentityPayment>,
        allocations: List<IdentityAllocation>
    ): List<IdentityIntegrityIssue> {
        val clientIds=clients.map { it.id }.toSet()
        val loanById=loans.groupBy { it.id }
        val paymentById=payments.groupBy { it.operationId }
        val issues=mutableListOf<IdentityIntegrityIssue>()
        loans.filter { it.clientId !in clientIds }.sortedBy { it.id }.forEach {
            issues += IdentityIntegrityIssue(IdentityIntegrityIssueType.LOAN_CLIENT_MISSING,it.id,"Loan references a missing client.")
        }
        payments.groupBy { it.operationId }.filter { it.key.isNotBlank() && it.value.size>1 }.toSortedMap().forEach { (id,_) ->
            issues += IdentityIntegrityIssue(IdentityIntegrityIssueType.DUPLICATE_PAYMENT_ID,id,"Payment operation ID is duplicated.")
        }
        payments.sortedBy { it.operationId }.forEach { payment ->
            val matchingLoans=loanById[payment.loanId].orEmpty()
            if(matchingLoans.isEmpty()) issues += IdentityIntegrityIssue(IdentityIntegrityIssueType.PAYMENT_LOAN_MISSING,payment.operationId,"Payment references a missing loan.")
            matchingLoans.filter { it.currency != payment.currency }.forEach {
                issues += IdentityIntegrityIssue(IdentityIntegrityIssueType.PAYMENT_CURRENCY_MISMATCH,payment.operationId,"Payment currency differs from the linked loan.")
            }
        }
        allocations.sortedWith(compareBy<IdentityAllocation>{it.operationId}.thenBy{it.loanId}.thenBy{it.installmentNumber}).forEach { allocation ->
            val payment=paymentById[allocation.operationId].orEmpty()
            if(payment.isEmpty()) issues += IdentityIntegrityIssue(IdentityIntegrityIssueType.ALLOCATION_PAYMENT_MISSING,allocation.operationId,"Allocation references a missing payment.")
            else if(payment.none { it.loanId==allocation.loanId }) issues += IdentityIntegrityIssue(IdentityIntegrityIssueType.ALLOCATION_LOAN_MISMATCH,allocation.operationId,"Allocation loan differs from the payment's loan.")
        }
        return issues.distinct().sortedWith(compareBy<IdentityIntegrityIssue>{it.type.name}.thenBy{it.key}.thenBy{it.details})
    }
}
