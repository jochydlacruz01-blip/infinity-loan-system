package com.infinityloan.finance

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentAllocationTest {

    @Test
    fun paymentIsAllocatedInCorrectPriorityOrder() {
        val result = PaymentAllocation.allocate(
            paymentMinor = 10000L,
            lateFeeDueMinor = 1000L,
            chargesDueMinor = 2000L,
            interestDueMinor = 3000L,
            principalDueMinor = 5000L
        )

        assertEquals(1000L, result.lateFeeMinor)
        assertEquals(2000L, result.chargesMinor)
        assertEquals(3000L, result.interestMinor)
        assertEquals(4000L, result.principalMinor)
        assertEquals(0L, result.unappliedMinor)
    }

    @Test
    fun partialPaymentCoversOnlyLateFeeAndCharges() {
        val result = PaymentAllocation.allocate(
            paymentMinor = 1500L,
            lateFeeDueMinor = 1000L,
            chargesDueMinor = 1000L,
            interestDueMinor = 2000L,
            principalDueMinor = 5000L
        )

        assertEquals(1000L, result.lateFeeMinor)
        assertEquals(500L, result.chargesMinor)
        assertEquals(0L, result.interestMinor)
        assertEquals(0L, result.principalMinor)
        assertEquals(0L, result.unappliedMinor)
    }

    @Test
    fun excessPaymentIsReturnedAsUnapplied() {
        val result = PaymentAllocation.allocate(
            paymentMinor = 10000L,
            lateFeeDueMinor = 1000L,
            chargesDueMinor = 1000L,
            interestDueMinor = 2000L,
            principalDueMinor = 3000L
        )

        assertEquals(1000L, result.lateFeeMinor)
        assertEquals(1000L, result.chargesMinor)
        assertEquals(2000L, result.interestMinor)
        assertEquals(3000L, result.principalMinor)
        assertEquals(3000L, result.unappliedMinor)
    }

    @Test
    fun allocatedAmountsAlwaysEqualOriginalPayment() {
        val payment = 12500L

        val result = PaymentAllocation.allocate(
            paymentMinor = payment,
            lateFeeDueMinor = 1500L,
            chargesDueMinor = 2000L,
            interestDueMinor = 3000L,
            principalDueMinor = 4000L
        )

        val total = result.lateFeeMinor +
            result.chargesMinor +
            result.interestMinor +
            result.principalMinor +
            result.unappliedMinor

        assertEquals(payment, total)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroPayment() {
        PaymentAllocation.allocate(0L, 0L, 0L, 0L, 1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativePayment() {
        PaymentAllocation.allocate(-100L, 0L, 0L, 0L, 1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeLateFee() {
        PaymentAllocation.allocate(1000L, -100L, 0L, 0L, 1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeCharges() {
        PaymentAllocation.allocate(1000L, 0L, -100L, 0L, 1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeInterest() {
        PaymentAllocation.allocate(1000L, 0L, 0L, -100L, 1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativePrincipal() {
        PaymentAllocation.allocate(1000L, 0L, 0L, 0L, -100L)
    }
}
