package com.infinityloan.data

import com.infinityloan.finance.MonthlyPaymentReport
import com.infinityloan.finance.MonthlyPaymentTotal
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Live Room-backed monthly payment report.
 *
 * The report recomputes whenever payments, loan currencies, or reversals change.
 * Reversed originals remain in payment history but are excluded from operational totals.
 */
class MonthlyPaymentReportRepository(
    private val payments: PaymentDao,
    private val loans: LoanDao,
    private val reversals: PaymentReversalDao,
    private val zone: ZoneId = ZoneId.of("UTC")
) {
    fun observe(): Flow<List<MonthlyPaymentTotal>> =
        combine(
            payments.observeAll(),
            loans.observeAll(),
            reversals.observeReversedOperationIds()
        ) { paymentRows, loanRows, reversedIds ->
            MonthlyPaymentReport.fromPayments(
                payments = paymentRows,
                loanCurrencies = loanRows.associate { it.id to it.currency },
                reversedOperationIds = reversedIds.toSet(),
                zone = zone
            )
        }
}
