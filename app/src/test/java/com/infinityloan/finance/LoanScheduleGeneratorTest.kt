package com.infinityloan.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.DateTimeException

class LoanScheduleGeneratorTest {
    private val principal = BigDecimal("1200.00")
    private val rate = BigDecimal("0.01")
    private val firstDueDate = LocalDate.of(2026, 1, 31)

    @Test
    fun monthlyScheduleUsesMonthlyDatesAndClampsMonthEnd() {
        val rows = LoanScheduleGenerator.generate(
            principal, rate, 3, "FRANCES",
            firstDueDate = firstDueDate
        )

        assertEquals(3, rows.size)
        assertEquals(LocalDate.of(2026, 1, 31), rows[0].dueDate)
        assertEquals(LocalDate.of(2026, 2, 28), rows[1].dueDate)
        assertEquals(LocalDate.of(2026, 3, 31), rows[2].dueDate)
    }

    @Test
    fun frenchAliasProducesSameScheduleAsSpanishMethodName() {
        val spanish = LoanScheduleGenerator.generate(
            principal, rate, 12, "FRANCES",
            firstDueDate = firstDueDate
        )
        val english = LoanScheduleGenerator.generate(
            principal, rate, 12, "FRENCH",
            firstDueDate = firstDueDate
        )

        assertEquals(spanish, english)
    }

    @Test
    fun weeklyAndBiweeklyFrequenciesUseSevenAndFourteenDays() {
        val weekly = LoanScheduleGenerator.generate(
            principal, rate, 3, "ITALIANO",
            frequency = LoanScheduleGenerator.Frequency.WEEKLY,
            firstDueDate = firstDueDate
        )
        val biweekly = LoanScheduleGenerator.generate(
            principal, rate, 3, "ITALIANO",
            frequency = LoanScheduleGenerator.Frequency.BIWEEKLY,
            firstDueDate = firstDueDate
        )

        assertEquals(LocalDate.of(2026, 2, 7), weekly[1].dueDate)
        assertEquals(LocalDate.of(2026, 2, 14), weekly[2].dueDate)
        assertEquals(LocalDate.of(2026, 2, 14), biweekly[1].dueDate)
        assertEquals(LocalDate.of(2026, 2, 28), biweekly[2].dueDate)
    }

    @Test
    fun scheduleReconcilesPrincipalAndRemainingBalance() {
        val rows = LoanScheduleGenerator.generate(
            principal, rate, 12, "FRANCES",
            firstDueDate = firstDueDate
        )

        assertEquals(0, rows.last().remainingPrincipal.compareTo(BigDecimal.ZERO.setScale(2)))
        assertTrue(rows.all { it.total.compareTo(it.principal.add(it.interest)) == 0 })
        assertTrue(rows.zipWithNext().all { (a, b) -> b.number == a.number + 1 })
    }

    @Test
    fun generatedSchedulesMaintainReconciliationAndChronologyAcrossMethodsAndFrequencies() {
        listOf("FRANCES", "ITALIANO", "AMERICANO").forEach { method ->
            LoanScheduleGenerator.Frequency.values().forEach { frequency ->
                val rows = LoanScheduleGenerator.generate(
                    BigDecimal("9876.54"), BigDecimal("0.0125"), 36, method,
                    frequency = frequency, firstDueDate = LocalDate.of(2026, 3, 30)
                )
                assertEquals(36, rows.size)
                assertEquals(0, rows.last().remainingPrincipal.compareTo(BigDecimal.ZERO))
                assertTrue(rows.all { row ->
                    row.principal >= BigDecimal.ZERO && row.interest >= BigDecimal.ZERO &&
                        row.remainingPrincipal >= BigDecimal.ZERO &&
                        row.total.compareTo(row.principal.add(row.interest)) == 0
                })
                assertTrue(rows.zipWithNext().all { (a, b) ->
                    b.number == a.number + 1 && b.dueDate.isAfter(a.dueDate)
                })
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownAmortizationMethod() {
        LoanScheduleGenerator.generate(
            principal, rate, 3, "DESCONOCIDO",
            firstDueDate = firstDueDate
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPrincipalThatRoundsToZeroAtRequestedScale() {
        LoanScheduleGenerator.generate(
            BigDecimal("0.001"), rate, 2, "FRANCES",
            firstDueDate = firstDueDate, scale = 2
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativePeriodicRate() {
        LoanScheduleGenerator.generate(
            principal, BigDecimal("-0.01"), 2, "FRANCES",
            firstDueDate = firstDueDate
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedMoneyScale() {
        LoanScheduleGenerator.generate(
            principal, rate, 2, "FRANCES",
            firstDueDate = firstDueDate, scale = 7
        )
    }
}
