package com.infinityloan.finance

import java.time.YearMonth

/** Combined read-only executive reporting row. Monetary values remain currency-specific. */
data class ExecutiveFinancialSummary(
    val currency: String,
    val activeLoans: Int,
    val outstandingPrincipalMinor: Long,
    val overdueInstallments: Int,
    val overdueMinor: Long,
    val collectedThisMonthMinor: Long,
    val paymentsThisMonth: Int,
    val reconciliationIssueCount: Int
)

/**
 * Combines previously validated report projections into an executive view.
 * It intentionally performs no FX conversion and does not mutate ledger records.
 */
object ExecutiveFinancialReport {
    fun build(
        portfolio: List<PortfolioCurrencySummary>,
        aging: List<CurrencyAgingSummary>,
        monthlyPayments: List<MonthlyPaymentTotal>,
        reconciliationIssues: List<CashReconciliationIssue>,
        month: YearMonth
    ): List<ExecutiveFinancialSummary> {
        val currencies = (portfolio.map { it.currency } + aging.map { it.currency } +
            monthlyPayments.filter { it.month == month }.map { it.currency }).distinct().sorted()
        val portfolioByCurrency = portfolio.associateBy { it.currency }
        val agingByCurrency = aging.associateBy { it.currency }
        val monthlyByCurrency = monthlyPayments.filter { it.month == month }.groupBy { it.currency }
        return currencies.map { currency ->
            val p = portfolioByCurrency[currency]
            val a = agingByCurrency[currency]
            val m = monthlyByCurrency[currency].orEmpty()
            ExecutiveFinancialSummary(
                currency = currency,
                activeLoans = p?.activeLoanCount ?: 0,
                outstandingPrincipalMinor = p?.outstandingPrincipalMinor ?: 0L,
                overdueInstallments = a?.buckets?.sumOf { it.installmentCount } ?: (p?.overdueInstallmentCount ?: 0),
                overdueMinor = a?.totalOverdueMinor ?: (p?.overdueScheduledMinor ?: 0L),
                collectedThisMonthMinor = m.fold(0L) { total, row -> Math.addExact(total, row.totalMinor) },
                paymentsThisMonth = m.sumOf { it.count },
                reconciliationIssueCount = reconciliationIssues.size
            )
        }
    }
}
