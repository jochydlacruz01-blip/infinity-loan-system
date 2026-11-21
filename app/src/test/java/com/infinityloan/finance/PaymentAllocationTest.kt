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
        @Test
    fun maximumPaymentWithNoOutstandingBalancesRemainsUnapplied() {
        val result = PaymentAllocation.allocate(
            paymentMinor = Long.MAX_VALUE,
            lateFeeDueMinor = 0L,
            chargesDueMinor = 0L,
            interestDueMinor = 0L,
            principalDueMinor = 0L
        )

        assertEquals(Long.MAX_VALUE, result.unappliedMinor)
        assertEquals(0L, result.lateFeeMinor)
        assertEquals(0L, result.chargesMinor)
        assertEquals(0L, result.interestMinor)
        assertEquals(0L, result.principalMinor)
    }

    @Test
    fun maximumPaymentIsAllocatedWithoutOverflow() {
        val result = PaymentAllocation.allocate(
            paymentMinor = Long.MAX_VALUE,
            lateFeeDueMinor = Long.MAX_VALUE - 1L,
            chargesDueMinor = Long.MAX_VALUE,
            interestDueMinor = Long.MAX_VALUE,
            principalDueMinor = Long.MAX_VALUE
        )

        assertEquals(Long.MAX_VALUE - 1L, result.lateFeeMinor)
        assertEquals(1L, result.chargesMinor)
        assertEquals(0L, result.interestMinor)
        assertEquals(0L, result.principalMinor)
        assertEquals(0L, result.unappliedMinor)
    }
    @Test
    fun allocationConservesEveryMinorUnitAcrossBoundaryScenarios() {
        val scenarios = listOf(
            longArrayOf(1L, 1L, 0L, 0L, 0L),
            longArrayOf(2L, 1L, 1L, 0L, 0L),
            longArrayOf(9L, 1L, 2L, 3L, 3L),
            longArrayOf(Long.MAX_VALUE, Long.MAX_VALUE - 3L, 1L, 1L, 1L),
            longArrayOf(Long.MAX_VALUE, 0L, 0L, 0L, 0L)
        )

        scenarios.forEach { values ->
            val result = PaymentAllocation.allocate(
                paymentMinor = values[0],
                lateFeeDueMinor = values[1],
                chargesDueMinor = values[2],
                interestDueMinor = values[3],
                principalDueMinor = values[4]
            )
            val distributed = listOf(
                result.lateFeeMinor,
                result.chargesMinor,
                result.interestMinor,
                result.principalMinor,
                result.unappliedMinor
            ).fold(0L, Math::addExact)

            assertEquals(values[0], distributed)
        }
    }

}
