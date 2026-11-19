package com.infinityloan.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OverdueBalanceTest {
    @Test fun unpaidScheduledAmountSubtractsPaidPrincipalAndInterest() {
        assertEquals(650L, OverdueBalance.remainingMinor(1000L, 200L, 150L))
    }

    @Test fun fullyPaidInstallmentHasZeroRemaining() {
        assertEquals(0L, OverdueBalance.remainingMinor(1000L, 700L, 300L))
    }

    @Test fun excessRecordedComponentsAreClampedAtZero() {
        assertEquals(0L, OverdueBalance.remainingMinor(100L, 200L, 50L))
    }

    @Test fun negativeInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            OverdueBalance.remainingMinor(-1L, 0L, 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OverdueBalance.remainingMinor(10L, -1L, 0L)
        }
    }

    @Test fun paidComponentsOverflowIsRejected() {
        assertThrows(ArithmeticException::class.java) {
            OverdueBalance.remainingMinor(10L, Long.MAX_VALUE, 1L)
        }
    }

    @Test fun subtractOverflowIsRejected() {
        assertThrows(ArithmeticException::class.java) {
            OverdueBalance.remainingMinor(Long.MAX_VALUE, Long.MAX_VALUE, 1L)
        }
    }
}
