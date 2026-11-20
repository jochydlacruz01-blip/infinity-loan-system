package com.infinityloan.finance

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.YearMonth

class MonthlyPaymentReportTest {
    @Test fun aggregatesByMonthAndCurrencyWithoutMixingCurrencies() {
        val report = MonthlyPaymentReport.aggregate(listOf(
            PaymentReportEntry("a", 100L, "DOP", 1_735_689_600_000L),
            PaymentReportEntry("b", 250L, "DOP", 1_735_689_600_000L),
            PaymentReportEntry("c", 500L, "USD", 1_735_689_600_000L)
        ), ZoneId.of("UTC"))
        assertEquals(2, report.size)
        assertEquals(350L, report.first { it.currency == "DOP" }.totalMinor)
        assertEquals(2, report.first { it.currency == "DOP" }.count)
        assertEquals(500L, report.first { it.currency == "USD" }.totalMinor)
    }

    @Test fun rejectsDuplicateIdsUnsupportedCurrencyAndNegativeAmount() {
        val valid = PaymentReportEntry("x", 1, "DOP", 0)
        try { MonthlyPaymentReport.aggregate(listOf(valid, valid)); throw AssertionError("duplicates") }
        catch (_: IllegalArgumentException) {}
        try { MonthlyPaymentReport.aggregate(listOf(valid.copy(currency="GBP"))); throw AssertionError("currency") }
        catch (_: IllegalArgumentException) {}
        try { MonthlyPaymentReport.aggregate(listOf(valid.copy(amountMinor=-1))); throw AssertionError("negative") }
        catch (_: IllegalArgumentException) {}
    }
}
