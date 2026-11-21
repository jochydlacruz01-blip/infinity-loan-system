package com.infinityloan.data

import com.infinityloan.finance.CashPaymentReconciliation
import com.infinityloan.finance.ReconciliationCashMovement
import com.infinityloan.finance.ReconciliationPayment

/**
 * Read-only adapter from persisted Room rows to the normalized diagnostic reconciler.
 * Only the integration-generated CASH-PAY- receipt namespace is treated as payment cash.
 * Manual movements and reversals are intentionally not interpreted as receipts.
 */
data class PersistedCashReconciliationReport(
    val checkedPayments: Int,
    val findings: List<com.infinityloan.finance.CashReconciliationIssue>
)

suspend fun InfinityDatabase.reconcilePersistedCashPayments(): PersistedCashReconciliationReport {
    val persistedPayments = cashSessions().findAllPaymentsForCashAudit()
    val reversedIds = paymentReversals().findAllReversedPaymentIds().toSet()
    val eligiblePayments = persistedPayments.filter { it.operationId !in reversedIds }
    val receiptRows = cashSessions().findAllMovementsForAudit().filter { row ->
        row.type == "CASH_IN" && row.operationId.startsWith("CASH-PAY-")
    }.filter { row ->
        val linkedId = row.operationId.removePrefix("CASH-PAY-")
        linkedId !in reversedIds
    }
    val payments = eligiblePayments.map { row ->
        ReconciliationPayment(row.operationId, row.amountMinor, row.currency)
    }
    val movements = receiptRows.map { row ->
        ReconciliationCashMovement(
            operationId = row.operationId,
            linkedPaymentOperationId = row.operationId.removePrefix("CASH-PAY-"),
            amountMinor = row.amountMinor,
            currency = row.sessionCurrency,
            type = if (row.type == "CASH_IN") "IN" else row.type
        )
    }
    return PersistedCashReconciliationReport(
        checkedPayments = payments.size,
        findings = CashPaymentReconciliation.reconcile(payments, movements)
    )
}
