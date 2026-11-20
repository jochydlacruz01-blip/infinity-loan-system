package com.infinityloan.finance

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortfolioAgingTest {
    private val today = LocalDate.parse("2026-11-20")

    @Test fun assignsExactAgeBoundariesAndSeparatesCurrency() {
        val result = PortfolioAging.build(listOf(
            AgingInstallment("DOP", today.minusDays(1), 100),
            AgingInstallment("DOP", today.minusDays(7), 200),
            AgingInstallment("DOP", today.minusDays(8), 300),
            AgingInstallment("DOP", today.minusDays(30), 400),
            AgingInstallment("DOP", today.minusDays(31), 500),
            AgingInstallment("DOP", today.minusDays(60), 600),
            AgingInstallment("DOP", today.minusDays(61), 700),
            AgingInstallment("USD", today.minusDays(3), 800)
        ), today)
        val dop = result.first { it.currency == "DOP" }
        assertEquals(listOf(300L,700L,1100L,700L), dop.buckets.map { it.unpaidMinor })
        assertEquals(2800L, dop.totalOverdueMinor)
        assertEquals(800L, result.first { it.currency == "USD" }.totalOverdueMinor)
    }

    @Test fun excludesDueTodayFutureAndZeroBalances() {
        val result = PortfolioAging.build(listOf(
            AgingInstallment("DOP", today, 100),
            AgingInstallment("DOP", today.plusDays(1), 200),
            AgingInstallment("DOP", today.minusDays(2), 0)
        ), today)
        assertEquals(0L, result.first().totalOverdueMinor)
    }

    @Test fun rejectsInvalidCurrencyAndNegativeBalance() {
        assertThrows(IllegalArgumentException::class.java) {
            PortfolioAging.build(listOf(AgingInstallment("GBP", today.minusDays(1), 1)), today)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PortfolioAging.build(listOf(AgingInstallment("DOP", today.minusDays(1), -1)), today)
        }
    }

    @Test fun detectsOverflow() {
        assertThrows(ArithmeticException::class.java) {
            PortfolioAging.build(listOf(
                AgingInstallment("DOP", today.minusDays(1), Long.MAX_VALUE),
                AgingInstallment("DOP", today.minusDays(2), 1)
            ), today)
        }
    }
    @Test fun outputIsStableAndIncludesEmptyBucketsForEachCurrency() {
        val rows = listOf(
            AgingInstallment("USD", today.minusDays(8), 20),
            AgingInstallment("DOP", today.minusDays(2), 10),
            AgingInstallment("DOP", today.minusDays(2), 15)
        )
        val result = PortfolioAging.build(rows, today)
        assertEquals(listOf("DOP", "USD"), result.map { it.currency })
        val dop = result.first()
        assertEquals(4, dop.buckets.size)
        assertEquals(2, dop.buckets.first().installmentCount)
        assertEquals(25L, dop.buckets.first().unpaidMinor)
        assertEquals(0, dop.buckets.last().installmentCount)
        assertEquals(0L, dop.buckets.last().unpaidMinor)
        assertEquals(20L, result.last().totalOverdueMinor)
    }
    @Test fun inputOrderAndListAreNotMutatedAndCurrencyRowsAreSorted() {
        val source = mutableListOf(
            AgingInstallment("EUR", today.minusDays(2), 30),
            AgingInstallment("DOP", today.minusDays(2), 10),
            AgingInstallment("USD", today.minusDays(2), 20)
        )
        val original = source.toList()
        val result = PortfolioAging.build(source, today)
        assertEquals(original, source)
        assertEquals(listOf("DOP", "EUR", "USD"), result.map { it.currency })
        assertEquals(10L, result.first { it.currency == "DOP" }.totalOverdueMinor)
        assertEquals(30L, result.first { it.currency == "EUR" }.totalOverdueMinor)
        assertEquals(20L, result.first { it.currency == "USD" }.totalOverdueMinor)
    }
}
