package com.infinityloan.finance

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class MinorUnitTotalsTest {

    @Test
    fun sumsValuesBeyondLongRangeWithoutOverflow() {
        val total = MinorUnitTotals.sum(listOf(Long.MAX_VALUE, Long.MAX_VALUE))

        assertEquals(BigDecimal("184467440737095516.14"), total)
    }

    @Test
    fun preservesNegativeCreditMovementsInNetTotal() {
        val total = MinorUnitTotals.sum(listOf(10_000L, -2_500L, -500L))

        assertEquals(BigDecimal("70.00"), total)
    }

    @Test
    fun emptyCollectionProducesZero() {
        assertEquals(BigDecimal("0.00"), MinorUnitTotals.sum(emptyList()))
    }
}
