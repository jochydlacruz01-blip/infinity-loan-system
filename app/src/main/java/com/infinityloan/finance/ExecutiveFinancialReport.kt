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
    private val supportedCurrencies = setOf("DOP", "USD", "EUR")

    fun build(
        portfolio: List<PortfolioCurrencySummary>,
        aging: List<CurrencyAgingSummary>,
        monthlyPayments: List<MonthlyPaymentTotal>,
        reconciliationIssues: List<CashReconciliationIssue>,
        month: YearMonth
    ): List<ExecutiveFinancialSummary> {
        require(portfolio.all { it.currency in supportedCurrencies }) { "Moneda de cartera no admitida" }
        require(aging.all { it.currency in supportedCurrencies }) { "Moneda de antigüedad no admitida" }
        require(monthlyPayments.all { it.currency in supportedCurrencies }) { "Moneda de pagos no admitida" }
        require(portfolio.map { it.currency }.distinct().size == portfolio.size) {
            "Hay más de un resumen de cartera para la misma moneda"
        }
        require(aging.map { it.currency }.distinct().size == aging.size) {
            "Hay más de un resumen de antigüedad para la misma moneda"
        }
        val currentMonthPayments = monthlyPayments.filter { it.month == month }
        require(currentMonthPayments.map { it.currency }.distinct().size == currentMonthPayments.size) {
            "Hay más de un total mensual para la misma moneda"
        }
        currentMonthPayments.forEach {
            require(it.totalMinor > 0L && it.count > 0) { "El total mensual debe tener importe y cantidad positivos" }
        }

        val currencies = (portfolio.map { it.currency } + aging.map { it.currency } +
            currentMonthPayments.map { it.currency }).distinct().sorted()
        val portfolioByCurrency = portfolio.associateBy { it.currency }
        val agingByCurrency = aging.associateBy { it.currency }
        val monthlyByCurrency = currentMonthPayments.associateBy { it.currency }
        return currencies.map { currency ->
            val p = portfolioByCurrency[currency]
            val a = agingByCurrency[currency]
            val m = monthlyByCurrency[currency]
            ExecutiveFinancialSummary(
                currency = currency,
                activeLoans = p?.activeLoanCount ?: 0,
                outstandingPrincipalMinor = p?.outstandingPrincipalMinor ?: 0L,
                overdueInstallments = a?.buckets?.fold(0) { total, row ->
                    Math.addExact(total, row.installmentCount)
                } ?: (p?.overdueInstallmentCount ?: 0),
                overdueMinor = a?.totalOverdueMinor ?: (p?.overdueScheduledMinor ?: 0L),
                collectedThisMonthMinor = m?.totalMinor ?: 0L,
                paymentsThisMonth = m?.count ?: 0,
                // Issues currently have no currency field, so this is a global count
                // repeated on each currency row, not a currency-specific allocation.
                reconciliationIssueCount = reconciliationIssues.size
            )
        }
    }
}
