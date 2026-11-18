package com.infinityloan.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class LoanCalculatorTest {
    private val principal = BigDecimal("50000.00")
    private val rate = BigDecimal("0.05")

    @Test fun frenchScheduleReconcilesPrincipalAndHasLevelPaymentsExceptRoundingAdjustment() {
        val rows = LoanCalculator.french(principal, rate, 12)
        assertEquals(12, rows.size)
        assertEquals(0, rows.first().total.compareTo(BigDecimal("5641.27")))
        assertEquals(0, rows.last().total.compareTo(BigDecimal("5641.29")))
        assertEquals(0, rows.fold(BigDecimal.ZERO) { sum, row -> sum + row.principal }.compareTo(principal))
        assertTrue(rows.all { it.total > BigDecimal.ZERO })
        assertTrue(rows.dropLast(1).all { it.total.compareTo(rows.first().total) == 0 })
    }

    @Test fun italianPrincipalIsRepaidExactlyAndPaymentsDecline() {
        val rows = LoanCalculator.italian(principal, rate, 12)
        assertEquals(0, rows.fold(BigDecimal.ZERO) { sum, row -> sum + row.principal }.compareTo(principal))
        assertTrue(rows.first().total > rows.last().total)
    }

    @Test fun americanPaysInterestUntilFinalPrincipal() {
        val rows = LoanCalculator.american(principal, rate, 12)
        assertEquals(0, rows.first().principal.compareTo(BigDecimal("0.00")))
        assertEquals(0, rows.first().interest.compareTo(BigDecimal("2500.00")))
        assertEquals(0, rows.last().total.compareTo(BigDecimal("52500.00")))
    }

    @Test fun zeroRateFrenchScheduleReconciles() {
        val rows = LoanCalculator.french(principal, BigDecimal.ZERO, 3)
        assertEquals(0, rows.fold(BigDecimal.ZERO) { sum, row -> sum + row.principal }.compareTo(principal))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeRate() {
        LoanCalculator.french(principal, BigDecimal("-0.01"), 12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroPeriods() {
        LoanCalculator.french(principal, rate, 0)
    }
    @Test
    fun allAmortizationMethodsReconcilePrincipalExactly() {
        val schedules = listOf(
            LoanCalculator.french(principal, rate, 12),
            LoanCalculator.italian(principal, rate, 12),
            LoanCalculator.american(principal, rate, 12)
        )

        schedules.forEach { rows ->
            val repaidPrincipal = rows.fold(BigDecimal.ZERO) { sum, row ->
                sum.add(row.principal)
            }

            assertEquals(0, repaidPrincipal.compareTo(principal))
            assertEquals(0, rows.last().principal.compareTo(rows.last().principal))
            assertTrue(rows.all {
                it.principal >= BigDecimal.ZERO &&
                it.interest >= BigDecimal.ZERO &&
                it.total.compareTo(it.principal.add(it.interest)) == 0
            })
        }
    }
}

