package com.infinityloan.finance

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Simple (non-compounding) contractual late-fee calculation.
 * A fee is calculated only after the grace period, on the unpaid scheduled
 * installment balance, capped by an optional maximum amount.
 */
data class LateFeePolicy(
    val enabled: Boolean,
    val graceDays: Int = 0,
    val dailyRate: BigDecimal = BigDecimal.ZERO,
    val fixedFeeMinor: Long = 0L,
    val maximumFeeMinor: Long? = null
) {
    init {
        require(graceDays >= 0)
        require(dailyRate >= BigDecimal.ZERO)
        require(fixedFeeMinor >= 0L)
        require(maximumFeeMinor == null || maximumFeeMinor >= 0L)
    }
}

object LateFeeCalculator {
    data class Result(val overdueDays: Long, val feeMinor: Long)

    fun calculate(
        policy: LateFeePolicy,
        dueDate: LocalDate,
        asOfDate: LocalDate,
        unpaidInstallmentMinor: Long
    ): Result {
        require(unpaidInstallmentMinor >= 0L)
        if (!policy.enabled || unpaidInstallmentMinor == 0L || asOfDate <= dueDate) {
            return Result(0L, 0L)
        }
        val daysLate = java.time.temporal.ChronoUnit.DAYS.between(dueDate, asOfDate)
        val chargeableDays = maxOf(0L, daysLate - policy.graceDays.toLong())
        if (chargeableDays == 0L) return Result(daysLate, 0L)

        val percentageFee = BigDecimal(unpaidInstallmentMinor)
            .multiply(policy.dailyRate)
            .multiply(BigDecimal(chargeableDays))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        val calculated = Math.addExact(percentageFee, policy.fixedFeeMinor)
        val capped = policy.maximumFeeMinor?.let { minOf(calculated, it) } ?: calculated
        return Result(daysLate, capped)
    }
}
