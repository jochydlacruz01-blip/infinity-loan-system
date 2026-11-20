package com.infinityloan.finance

/**
 * Normalized inputs for auditing the relationship between posted payments and cash movements.
 * These models deliberately do not mutate ledgers or create/reverse financial operations.
 */
data class ReconciliationPayment(
    val operationId: String,
    val amountMinor: Long,
    val currency: String
)

data class ReconciliationCashMovement(
    val operationId: String,
    val linkedPaymentOperationId: String?,
    val amountMinor: Long,
    val currency: String,
    val type: String
)

enum class CashReconciliationIssueType {
    MISSING_CASH_MOVEMENT,
    AMOUNT_MISMATCH,
    CURRENCY_MISMATCH,
    DUPLICATE_CASH_MOVEMENT,
    ORPHAN_CASH_MOVEMENT,
    INVALID_PAYMENT,
    DUPLICATE_PAYMENT_OPERATION,
    INVALID_CASH_MOVEMENT
}

data class CashReconciliationIssue(
    val type: CashReconciliationIssueType,
    val paymentOperationId: String?,
    val cashMovementOperationId: String?,
    val expectedMinor: Long?,
    val actualMinor: Long?,
    val details: String
)

/** Pure, deterministic reconciliation report. No financial data is modified. */
private val supportedCurrencies = setOf("DOP", "USD", "EUR")
object CashPaymentReconciliation {
    fun reconcile(
        payments: List<ReconciliationPayment>,
        movements: List<ReconciliationCashMovement>
    ): List<CashReconciliationIssue> {
        val issues = mutableListOf<CashReconciliationIssue>()
        val paymentById = payments.groupBy { it.operationId }
        payments.groupBy { it.operationId }.filter { it.value.size > 1 }
            .toSortedMap().forEach { (operationId, duplicates) ->
                issues += CashReconciliationIssue(
                    CashReconciliationIssueType.DUPLICATE_PAYMENT_OPERATION, operationId,
                    null, duplicates.first().amountMinor, null,
                    "Multiple payment records share the same operation id."
                )
            }
        movements.filter { it.operationId.isBlank() || it.amountMinor <= 0L ||
            it.currency !in supportedCurrencies || it.type.isBlank()
        }.sortedBy { it.operationId }.forEach { movement ->
            issues += CashReconciliationIssue(
                CashReconciliationIssueType.INVALID_CASH_MOVEMENT,
                movement.linkedPaymentOperationId, movement.operationId,
                null, movement.amountMinor, "Cash movement has invalid operation id, amount, supported currency, or type."
            )
        }
        val linked = movements.filter { it.linkedPaymentOperationId != null }
            .groupBy { it.linkedPaymentOperationId!! }

        payments.sortedBy { it.operationId }.forEach { payment ->
            if ((paymentById[payment.operationId]?.size ?: 0) > 1) return@forEach
            if (payment.operationId.isBlank() || payment.amountMinor <= 0L || payment.currency !in supportedCurrencies) {
                issues += CashReconciliationIssue(
                    CashReconciliationIssueType.INVALID_PAYMENT, payment.operationId,
                    null, payment.amountMinor, null, "Payment has invalid operation id, amount, or supported currency."
                )
                return@forEach
            }
            val group = linked[payment.operationId].orEmpty().sortedBy { it.operationId }
            if (group.isEmpty()) {
                issues += CashReconciliationIssue(
                    CashReconciliationIssueType.MISSING_CASH_MOVEMENT, payment.operationId,
                    null, payment.amountMinor, null, "No cash movement is linked to this payment."
                )
                return@forEach
            }
            if (group.size > 1) {
                group.drop(1).forEach { duplicate ->
                    issues += CashReconciliationIssue(
                        CashReconciliationIssueType.DUPLICATE_CASH_MOVEMENT,
                        payment.operationId, duplicate.operationId, payment.amountMinor,
                        duplicate.amountMinor, "Multiple cash movements are linked to this payment."
                    )
                }
            }
            val movement = group.first()
            if (movement.amountMinor != payment.amountMinor) {
                issues += CashReconciliationIssue(
                    CashReconciliationIssueType.AMOUNT_MISMATCH, payment.operationId,
                    movement.operationId, payment.amountMinor, movement.amountMinor,
                    "Cash movement amount differs from payment amount."
                )
            }
            if (movement.currency != payment.currency) {
                issues += CashReconciliationIssue(
                    CashReconciliationIssueType.CURRENCY_MISMATCH, payment.operationId,
                    movement.operationId, payment.amountMinor, movement.amountMinor,
                    "Cash movement currency differs from payment currency."
                )
            }
        }
        movements.filter { it.linkedPaymentOperationId != null &&
            (paymentById[it.linkedPaymentOperationId]?.size ?: 0) == 0
        }.sortedBy { it.operationId }.forEach { movement ->
            issues += CashReconciliationIssue(
                CashReconciliationIssueType.ORPHAN_CASH_MOVEMENT,
                movement.linkedPaymentOperationId, movement.operationId, null,
                movement.amountMinor, "Cash movement references a payment not present in the reconciliation input."
            )
        }
        return issues
    }
}
