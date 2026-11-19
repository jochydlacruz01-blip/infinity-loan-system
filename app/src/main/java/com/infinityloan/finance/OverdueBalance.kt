package com.infinityloan.finance

/** Checked calculation of the unpaid scheduled principal and interest, in minor units. */
object OverdueBalance {
    fun remainingMinor(totalMinor: Long, paidPrincipalMinor: Long, paidInterestMinor: Long): Long {
        require(totalMinor >= 0L) { "Installment total cannot be negative." }
        require(paidPrincipalMinor >= 0L) { "Paid principal cannot be negative." }
        require(paidInterestMinor >= 0L) { "Paid interest cannot be negative." }
        val paid = Math.addExact(paidPrincipalMinor, paidInterestMinor)
        return maxOf(0L, Math.subtractExact(totalMinor, paid))
    }
}
