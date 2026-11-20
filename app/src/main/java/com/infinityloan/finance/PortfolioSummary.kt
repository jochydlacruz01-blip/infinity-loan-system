package com.infinityloan.finance

import java.time.LocalDate
import java.util.Locale

/** Currency-separated portfolio indicators; no implicit FX conversion is performed. */
data class PortfolioLoanRecord(
    val currency: String,
    val status: String,
    val outstandingPrincipalMinor: Long
)

data class PortfolioInstallmentRecord(
    val currency: String,
    val dueDate: LocalDate,
    val unpaidScheduledMinor: Long
)

data class PortfolioCurrencySummary(
    val currency: String,
    val loanCount: Int,
    val activeLoanCount: Int,
    val outstandingPrincipalMinor: Long,
    val overdueInstallmentCount: Int,
    val overdueScheduledMinor: Long
)

/**
 * Builds a deterministic dashboard/report projection from persisted read-model rows.
 * The caller supplies the business date (not the device clock) and currency-tagged
 * minor-unit amounts. The backend will become authoritative for that date after sync.
 */
object PortfolioSummary {
    private val supportedCurrencies = setOf("DOP", "USD", "EUR")

    fun build(
        loans: Iterable<PortfolioLoanRecord>,
        installments: Iterable<PortfolioInstallmentRecord>,
        asOfDate: LocalDate
    ): List<PortfolioCurrencySummary> {
        val loanRows = loans.toList()
        val installmentRows = installments.toList()

        loanRows.forEach { loan ->
            require(loan.currency in supportedCurrencies) { "Moneda de cartera no admitida: ${loan.currency}" }
            require(loan.status.isNotBlank()) { "El estado del préstamo es obligatorio" }
            require(loan.outstandingPrincipalMinor >= 0L) { "El saldo de capital no puede ser negativo" }
        }
        installmentRows.forEach { installment ->
            require(installment.currency in supportedCurrencies) {
                "Moneda de cuota no admitida: ${installment.currency}"
            }
            require(installment.unpaidScheduledMinor >= 0L) { "El saldo pendiente de cuota no puede ser negativo" }
        }

        val currencies = (loanRows.map { it.currency } + installmentRows.map { it.currency }).toSortedSet()
        return currencies.map { currency ->
            val currencyLoans = loanRows.filter { it.currency == currency }
            val activeLoans = currencyLoans.filter { it.status.equals("ACTIVE", ignoreCase = true) }
            val overdue = installmentRows.filter {
                it.currency == currency && it.dueDate.isBefore(asOfDate) && it.unpaidScheduledMinor > 0L
            }

            PortfolioCurrencySummary(
                currency = currency,
                loanCount = currencyLoans.size,
                activeLoanCount = activeLoans.size,
                outstandingPrincipalMinor = activeLoans.fold(0L) { total, loan ->
                    Math.addExact(total, loan.outstandingPrincipalMinor)
                },
                overdueInstallmentCount = overdue.size,
                overdueScheduledMinor = overdue.fold(0L) { total, installment ->
                    Math.addExact(total, installment.unpaidScheduledMinor)
                }
            )
        }
    }
}
