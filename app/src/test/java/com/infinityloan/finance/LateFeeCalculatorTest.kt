package com.infinityloan.finance

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertThrows
import java.math.BigDecimal
import java.time.LocalDate

class LateFeeCalculatorTest {
    private val due = LocalDate.parse("2026-01-01")

    @Test fun disabledPolicyProducesNoFee() {
        assertEquals(0L, LateFeeCalculator.calculate(LateFeePolicy(false, dailyRate=BigDecimal("0.01")), due, due.plusDays(10), 10000).feeMinor)
    }
    @Test fun gracePeriodExcludesGraceDays() {
        val r=LateFeeCalculator.calculate(LateFeePolicy(true, graceDays=3, dailyRate=BigDecimal("0.01")), due, due.plusDays(5), 10000)
        assertEquals(5L,r.overdueDays); assertEquals(200L,r.feeMinor)
    }
    @Test fun simpleRateDoesNotCompound() {
        val r=LateFeeCalculator.calculate(LateFeePolicy(true, dailyRate=BigDecimal("0.01")), due, due.plusDays(3), 10000)
        assertEquals(300L,r.feeMinor)
    }
    @Test fun fixedFeeAndCapApply() {
        val r=LateFeeCalculator.calculate(LateFeePolicy(true, dailyRate=BigDecimal("0.10"), fixedFeeMinor=100, maximumFeeMinor=500), due, due.plusDays(4), 10000)
        assertEquals(500L,r.feeMinor)
    }
    @Test fun noFeeOnDueDateOrPaidInstallment() {
        assertEquals(0L,LateFeeCalculator.calculate(LateFeePolicy(true,dailyRate=BigDecimal("0.1")), due, due, 10000).feeMinor)
        assertEquals(0L,LateFeeCalculator.calculate(LateFeePolicy(true,dailyRate=BigDecimal("0.1")), due, due.plusDays(3), 0).feeMinor)
    }
    @Test fun invalidPolicyValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            LateFeePolicy(true, graceDays = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LateFeePolicy(true, dailyRate = BigDecimal("-0.01"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LateFeePolicy(true, fixedFeeMinor = -1)
        }
    }

    @Test fun negativeUnpaidBalanceIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            LateFeeCalculator.calculate(LateFeePolicy(true), due, due.plusDays(2), -1)
        }
    }

    @Test fun integerOverflowIsRejectedRatherThanWrapped() {
        assertThrows(ArithmeticException::class.java) {
            LateFeeCalculator.calculate(
                LateFeePolicy(true, dailyRate = BigDecimal("100000000000000000000")),
                due, due.plusDays(100), Long.MAX_VALUE
            )
        }
    }

}
