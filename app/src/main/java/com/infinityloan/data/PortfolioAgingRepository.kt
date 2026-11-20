package com.infinityloan.data

import com.infinityloan.finance.AgingInstallment
import com.infinityloan.finance.CurrencyAgingSummary
import com.infinityloan.finance.PortfolioAging
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Local, currency-separated overdue aging projection derived from Room schedules. */
class PortfolioAgingRepository(private val loans: LoanDao, private val installments: LoanInstallmentDao) {
    fun observe(asOfDate: LocalDate): Flow<List<CurrencyAgingSummary>> =
        combine(loans.observeAll(), installments.observeAll()) { loanRows, installmentRows ->
            val loanById = loanRows.associateBy { it.id }
            PortfolioAging.build(installmentRows.map { row ->
                val loan = requireNotNull(loanById[row.loanId]) { "Una cuota no tiene préstamo asociado" }
                val paid = Math.addExact(row.paidPrincipalMinor, row.paidInterestMinor)
                val unpaid = Math.subtractExact(row.totalMinor, paid)
                require(unpaid >= 0L) { "Los pagos aplicados superan el total programado" }
                AgingInstallment(loan.currency, LocalDate.parse(row.dueDate), unpaid)
            }, asOfDate)
        }
}
