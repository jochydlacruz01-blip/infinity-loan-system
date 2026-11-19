package com.infinityloan.finance

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Adds stored minor-unit amounts without overflowing a signed 64-bit accumulator.
 * The returned decimal is expressed in major currency units with scale 2.
 */
object MinorUnitTotals {
    fun sum(amounts: Iterable<Long>): BigDecimal {
        val totalMinor = amounts.fold(BigInteger.ZERO) { total, amount ->
            total.add(BigInteger.valueOf(amount))
        }
        return BigDecimal(totalMinor, 2)
    }
}
