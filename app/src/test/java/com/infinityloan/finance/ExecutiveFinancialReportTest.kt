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
}
