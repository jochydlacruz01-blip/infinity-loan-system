package com.infinityloan.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import com.infinityloan.data.PaymentEntity
import org.junit.Test
import java.time.ZoneId
import java.time.YearMonth

class MonthlyPaymentReportTest {
    @Test fun buildsTotalsFromStoredPaymentsAndExcludesReversals() {
        val payments = listOf(
            PaymentEntity("p1", "loan-dop", 100L, "R-1", 1_735_689_600_000L),
            PaymentEntity("p2", "loan-dop", 250L, "R-2", 1_735_689_600_000L),
            PaymentEntity("p3", "loan-usd", 500L, "R-3", 1_735_689_600_000L)
        )
        val report = MonthlyPaymentReport.fromPayments(
            payments, mapOf("loan-dop" to "DOP", "loan-usd" to "USD"),
            setOf("p2"), ZoneId.of("UTC")
        )
        assertEquals(2, report.size)
        assertEquals(100L, report.first { it.currency == "DOP" }.totalMinor)
        assertEquals(1, report.first { it.currency == "DOP" }.count)
        assertEquals(500L, report.first { it.currency == "USD" }.totalMinor)
    }

    @Test fun failsWhenPaymentLoanCurrencyCannotBeResolved() {
        val payment = PaymentEntity("p1", "missing-loan", 100L, "R-1", 0L)
        try {
            MonthlyPaymentReport.fromPayments(listOf(payment), emptyMap(), emptySet())
            throw AssertionError("missing loan currency")
        } catch (_: IllegalArgumentException) {}
    }
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
        try { MonthlyPaymentReport.aggregate(listOf(valid.copy(amountMinor=0))); throw AssertionError("zero") }
        catch (_: IllegalArgumentException) {}
    }
    @Test fun rejectsMonthlyTotalOverflowInsteadOfWrappingNegative() {
        val entries = listOf(
            PaymentReportEntry("max-1", Long.MAX_VALUE, "DOP", 1_735_689_600_000L),
            PaymentReportEntry("max-2", 1L, "DOP", 1_735_689_600_000L)
        )
        try {
            MonthlyPaymentReport.aggregate(entries)
            throw AssertionError("overflow must be rejected")
        } catch (_: ArithmeticException) {}
    }
}
