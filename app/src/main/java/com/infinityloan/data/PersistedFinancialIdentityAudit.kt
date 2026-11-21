package com.infinityloan.data

import com.infinityloan.finance.*

/** Runs a read-only audit snapshot across persisted identity relationships. */
data class PersistedIdentityAuditReport(
    val clientsChecked: Int,
    val loansChecked: Int,
    val paymentsChecked: Int,
    val allocationsChecked: Int,
    val dashboard: IdentityAuditDashboard,
    val findings: List<IdentityIntegrityIssue>
)

suspend fun InfinityDatabase.auditPersistedFinancialIdentity(): PersistedIdentityAuditReport {
    val clients=clients().findAllForIntegrityAudit()
    val loans=loans().findAllForIntegrityAudit()
    val payments=payments().findAllForIntegrityAudit()
    val allocations=paymentAllocations().findAllForIntegrityAudit()
    val findings=FinancialIdentityIntegrity.audit(
        clients.map { IdentityClient(it.id) },
        loans.map { IdentityLoan(it.id,it.clientId,it.currency) },
        payments.map { IdentityPayment(it.operationId,it.loanId, loans.firstOrNull { loan -> loan.id==it.loanId }?.currency.orEmpty()) },
        allocations.map { IdentityAllocation(it.operationId,it.loanId,it.installmentNumber) }
    )
    return PersistedIdentityAuditReport(
        clients.size,loans.size,payments.size,allocations.size,
        IdentityAuditDashboardBuilder.build(findings),findings
    )
}
