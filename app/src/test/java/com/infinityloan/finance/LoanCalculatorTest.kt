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
                     assertTrue(rows.all {
                it.principal >= BigDecimal.ZERO &&
                it.interest >= BigDecimal.ZERO &&
                it.total.compareTo(it.principal.add(it.interest)) == 0
            })
        }
    }
        @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroPrincipal() {
        LoanCalculator.french(BigDecimal.ZERO, rate, 12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativePrincipal() {
        LoanCalculator.french(BigDecimal("-1000.00"), rate, 12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidMonetaryScale() {
        LoanCalculator.french(principal, rate, 12, 7)
    }
        @Test
    fun allAmortizationMethodsRejectInvalidInputs() {
        val methods: List<(BigDecimal, BigDecimal, Int) -> List<LoanCalculator.Installment>> = listOf(
    { p, r, n -> LoanCalculator.french(p, r, n) },
    { p, r, n -> LoanCalculator.italian(p, r, n) },
    { p, r, n -> LoanCalculator.american(p, r, n) }
)

        methods.forEach { method ->
            try {
                method(BigDecimal.ZERO, rate, 12)
                throw AssertionError("Debe rechazar capital cero")
            } catch (_: IllegalArgumentException) {
            }

            try {
                method(principal, BigDecimal("-0.01"), 12)
                throw AssertionError("Debe rechazar tasa negativa")
            } catch (_: IllegalArgumentException) {
            }

            try {
                method(principal, rate, 0)
                throw AssertionError("Debe rechazar períodos cero")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
        @Test
    fun maximumPeriodCountIsAcceptedByAllMethods() {
        val methods: List<(BigDecimal, BigDecimal, Int) -> List<LoanCalculator.Installment>> = listOf(
            { p, r, n -> LoanCalculator.french(p, r, n) },
            { p, r, n -> LoanCalculator.italian(p, r, n) },
            { p, r, n -> LoanCalculator.american(p, r, n) }
        )

        methods.forEach { method ->
            val rows = method(principal, rate, 600)
            assertEquals(600, rows.size)
        }
    }

    @Test
    fun allMethodsRejectMoreThan600Periods() {
        val methods: List<(BigDecimal, BigDecimal, Int) -> List<LoanCalculator.Installment>> = listOf(
            { p, r, n -> LoanCalculator.french(p, r, n) },
            { p, r, n -> LoanCalculator.italian(p, r, n) },
            { p, r, n -> LoanCalculator.american(p, r, n) }
        )

        methods.forEach { method ->
            try {
                method(principal, rate, 601)
                throw AssertionError("Debe rechazar más de 600 períodos")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun allMethodsRejectNegativePeriods() {
        val methods: List<(BigDecimal, BigDecimal, Int) -> List<LoanCalculator.Installment>> = listOf(
            { p, r, n -> LoanCalculator.french(p, r, n) },
            { p, r, n -> LoanCalculator.italian(p, r, n) },
            { p, r, n -> LoanCalculator.american(p, r, n) }
        )

        methods.forEach { method ->
            try {
                method(principal, rate, -1)
                throw AssertionError("Debe rechazar períodos negativos")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
    @Test
    fun allAmortizationMethodsReconcileSmallPrincipalAtZeroRate() {
        val smallPrincipal = BigDecimal("0.05")
        val zeroRate = BigDecimal.ZERO
        val schedules = listOf(
            LoanCalculator.french(smallPrincipal, zeroRate, 3),
            LoanCalculator.italian(smallPrincipal, zeroRate, 3),
            LoanCalculator.american(smallPrincipal, zeroRate, 3)
        )

        schedules.forEach { rows ->
            assertEquals(3, rows.size)
            val repaid = rows.fold(BigDecimal.ZERO) { sum, row ->
                sum.add(row.principal)
            }
            assertEquals(0, repaid.compareTo(smallPrincipal))
            assertTrue(rows.all { row ->
                row.principal >= BigDecimal.ZERO &&
                    row.interest.compareTo(BigDecimal.ZERO) == 0 &&
                    row.total.compareTo(row.principal) == 0
            })
        }
    }

    @Test
    fun singlePeriodScheduleIncludesRoundedPrincipalAndInterest() {
        val schedules = listOf(
            LoanCalculator.french(BigDecimal("123.456"), BigDecimal("0.075"), 1, 2),
            LoanCalculator.italian(BigDecimal("123.456"), BigDecimal("0.075"), 1, 2),
            LoanCalculator.american(BigDecimal("123.456"), BigDecimal("0.075"), 1, 2)
        )
        schedules.forEach { rows ->
            assertEquals(1, rows.size)
            assertEquals(0, rows.single().principal.compareTo(BigDecimal("123.46")))
            assertEquals(0, rows.single().interest.compareTo(BigDecimal("9.26")))
            assertEquals(0, rows.single().total.compareTo(BigDecimal("132.72")))
        }
    }

    @Test
    fun zeroScaleSchedulesReconcileRoundedPrincipal() {
        val p = BigDecimal("101.49")
        val schedules = listOf(
            LoanCalculator.french(p, BigDecimal.ZERO, 7, 0),
            LoanCalculator.italian(p, BigDecimal.ZERO, 7, 0),
            LoanCalculator.american(p, BigDecimal.ZERO, 7, 0)
        )
        schedules.forEach { rows ->
            assertEquals(0, rows.fold(BigDecimal.ZERO) { sum, row -> sum + row.principal }.compareTo(BigDecimal("101")))
            assertTrue(rows.all { it.principal >= BigDecimal.ZERO && it.interest >= BigDecimal.ZERO })
        }
    }

    @Test
    fun variedSchedulesReconcileRoundedPrincipalAndRemainNonnegative() {
        val cases = listOf(
            Triple(BigDecimal("999.995"), BigDecimal("0.015"), 5),
            Triple(BigDecimal("0.11"), BigDecimal("0.02"), 4),
            Triple(BigDecimal("10000.555"), BigDecimal("0.0"), 17),
            Triple(BigDecimal("321.789"), BigDecimal("0.125"), 9),
            Triple(BigDecimal("75.01"), BigDecimal("0.035"), 2)
        )
        cases.forEach { (rawPrincipal, periodicRate, periods) ->
            val roundedPrincipal = rawPrincipal.setScale(2, java.math.RoundingMode.HALF_UP)
            val schedules = listOf(
                LoanCalculator.french(rawPrincipal, periodicRate, periods),
                LoanCalculator.italian(rawPrincipal, periodicRate, periods),
                LoanCalculator.american(rawPrincipal, periodicRate, periods)
            )
            schedules.forEach { rows ->
                assertEquals(periods, rows.size)
                assertEquals(0, rows.fold(BigDecimal.ZERO) { sum, row -> sum + row.principal }.compareTo(roundedPrincipal))
                assertTrue(rows.all { row ->
                    row.principal >= BigDecimal.ZERO &&
                        row.interest >= BigDecimal.ZERO &&
                        row.total.compareTo(row.principal.add(row.interest)) == 0
                })
            }
        }
    }

}
