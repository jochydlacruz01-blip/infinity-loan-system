package com.infinityloan.finance

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortfolioSummaryTest {
    private val today = LocalDate.parse("2026-11-20")

    @Test
    fun separatesCurrenciesAndCountsOnlyActiveLoanPrincipal() {
        val result = PortfolioSummary.build(
            loans = listOf(
                PortfolioLoanRecord("DOP", "ACTIVE", 10_000L),
                PortfolioLoanRecord("DOP", "CLOSED", 50_000L),
                PortfolioLoanRecord("USD", "ACTIVE", 2_500L)
            ),
            installments = emptyList(),
            asOfDate = today
        )
        assertEquals(listOf("DOP", "USD"), result.map { it.currency })
        assertEquals(2, result[0].loanCount)
        assertEquals(1, result[0].activeLoanCount)
        assertEquals(10_000L, result[0].outstandingPrincipalMinor)
        assertEquals(2_500L, result[1].outstandingPrincipalMinor)
    }

    @Test
    fun countsOnlyPastDueInstallmentsWithAnUnpaidBalance() {
        val result = PortfolioSummary.build(
            loans = emptyList(),
            installments = listOf(
                PortfolioInstallmentRecord("DOP", today.minusDays(1), 500L),
                PortfolioInstallmentRecord("DOP", today, 700L),
                PortfolioInstallmentRecord("DOP", today.minusDays(4), 0L),
                PortfolioInstallmentRecord("EUR", today.minusDays(2), 250L)
            ),
            asOfDate = today
        )
        val dop = result.first { it.currency == "DOP" }
        assertEquals(1, dop.overdueInstallmentCount)
        assertEquals(500L, dop.overdueScheduledMinor)
        assertEquals(250L, result.first { it.currency == "EUR" }.overdueScheduledMinor)
    }

    @Test
    fun emptyPortfolioProducesNoCurrencyRows() {
        assertEquals(emptyList<PortfolioCurrencySummary>(), PortfolioSummary.build(emptyList(), emptyList(), today))
    }

    @Test
    fun rejectsUnsupportedCurrencyAndNegativeBalances() {
        assertThrows(IllegalArgumentException::class.java) {
            PortfolioSummary.build(listOf(PortfolioLoanRecord("GBP", "ACTIVE", 100L)), emptyList(), today)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PortfolioSummary.build(listOf(PortfolioLoanRecord("DOP", "ACTIVE", -1L)), emptyList(), today)
        }
    }

    @Test
    fun detectsOverflowInsteadOfReturningCorruptedTotals() {
        assertThrows(ArithmeticException::class.java) {
            PortfolioSummary.build(
                listOf(
                    PortfolioLoanRecord("DOP", "ACTIVE", Long.MAX_VALUE),
                    PortfolioLoanRecord("DOP", "ACTIVE", 1L)
                ),
                emptyList(),
                today
            )
        }
    }
}
