package com.infinityloan.finance

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ExecutiveFinancialReportTest {
    @Test fun combinesProjectionsWithoutMixingCurrencies() {
        val month = YearMonth.of(2026, 11)
        val portfolio = listOf(PortfolioCurrencySummary("DOP", 5, 4, 9000, 2, 600))
        val aging = listOf(CurrencyAgingSummary("DOP", AgingBucket.entries.map {
            AgingBucketTotal(it, if (it == AgingBucket.DAYS_1_7) 2 else 0,
                if (it == AgingBucket.DAYS_1_7) 600L else 0L)
        }, 600))
        val payments = listOf(MonthlyPaymentTotal(month, "DOP", 1200, 3),
            MonthlyPaymentTotal(month, "USD", 500, 1))
        val issues = listOf(CashReconciliationIssue(CashReconciliationIssueType.MISSING_CASH_MOVEMENT,
            "p", null, 100, null, "missing"))
        val report = ExecutiveFinancialReport.build(portfolio, aging, payments, issues, month)
        assertEquals(listOf("DOP", "USD"), report.map { it.currency })
        assertEquals(9000L, report.first().outstandingPrincipalMinor)
        assertEquals(1200L, report.first().collectedThisMonthMinor)
        assertEquals(3, report.first().paymentsThisMonth)
        assertEquals(1, report.first().reconciliationIssueCount)
        assertEquals(500L, report.last().collectedThisMonthMinor)
        assertEquals(0, report.last().activeLoans)
    }

    @Test fun emptyInputsReturnEmptyReport() {
        assertEquals(emptyList<ExecutiveFinancialSummary>(),
            ExecutiveFinancialReport.build(emptyList(), emptyList(), emptyList(), emptyList(), YearMonth.of(2026, 11)))
    }

    @Test fun rejectsDuplicatePortfolioCurrencyInsteadOfSilentlyKeepingLast() {
        val month = YearMonth.of(2026, 11)
        val duplicate = listOf(
            PortfolioCurrencySummary("DOP", 1, 1, 100, 0, 0),
            PortfolioCurrencySummary("DOP", 2, 2, 200, 0, 0)
        )
        try {
            ExecutiveFinancialReport.build(duplicate, emptyList(), emptyList(), emptyList(), month)
            throw AssertionError("duplicate currency summaries must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsDuplicateMonthlyCurrencyTotals() {
        val month = YearMonth.of(2026, 11)
        val duplicate = listOf(
            MonthlyPaymentTotal(month, "DOP", 100, 1),
            MonthlyPaymentTotal(month, "DOP", 200, 2)
        )
        try {
            ExecutiveFinancialReport.build(emptyList(), emptyList(), duplicate, emptyList(), month)
            throw AssertionError("duplicate monthly currency totals must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsUnsupportedCurrencyInExecutiveInputs() {
        val month = YearMonth.of(2026, 11)
        try {
            ExecutiveFinancialReport.build(emptyList(), emptyList(),
                listOf(MonthlyPaymentTotal(month, "XYZ", 100, 1)), emptyList(), month)
            throw AssertionError("unsupported currency must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
