package com.infinityloan.finance

import com.infinityloan.data.PaymentEntity

/**
 * Excludes reversed originals from operational performance totals.
 * The original payment remains available to the payment history/audit UI.
 */
object PaymentReporting {
    fun effectivePayments(
        payments: List<PaymentEntity>,
        reversedOperationIds: Set<String>
    ): List<PaymentEntity> = payments.filterNot { it.operationId in reversedOperationIds }
}
