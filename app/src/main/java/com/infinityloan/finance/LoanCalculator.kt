package com.infinityloan.finance

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Deterministic amortization schedules.
 * Rates are periodic decimal fractions: 5% = BigDecimal("0.05").
 * Monetary values are rounded to `scale` decimals using HALF_UP.
 */
object LoanCalculator {
    private val MC = MathContext(34, RoundingMode.HALF_EVEN)

    data class Installment(
        val number: Int,
        val principal: BigDecimal,
        val interest: BigDecimal,
        val total: BigDecimal
    )

    fun french(
        principal: BigDecimal,
        periodicRate: BigDecimal,
        periods: Int,
        scale: Int = 2
    ): List<Installment> {
        requireInputs(principal, periodicRate, periods, scale)
        val payment = if (periodicRate.compareTo(BigDecimal.ZERO) == 0) {
            principal.divide(BigDecimal(periods), MC)
        } else {
            val factor = BigDecimal.ONE.add(periodicRate, MC).pow(periods, MC)
            principal.multiply(periodicRate, MC)
                .multiply(factor, MC)
                .divide(factor.subtract(BigDecimal.ONE, MC), MC)
        }
        return buildSchedule(principal, periods, scale) { balance, _ ->
            val interest = balance.multiply(periodicRate, MC).setScale(scale, RoundingMode.HALF_UP)
            val regularPrincipal = payment.setScale(scale, RoundingMode.HALF_UP)
                .subtract(interest).setScale(scale, RoundingMode.HALF_UP)
            regularPrincipal to interest
        }
    }

    fun italian(
        principal: BigDecimal,
        periodicRate: BigDecimal,
        periods: Int,
        scale: Int = 2
    ): List<Installment> {
        requireInputs(principal, periodicRate, periods, scale)
        val principalPart = principal.divide(BigDecimal(periods), MC)
        return buildSchedule(principal, periods, scale) { balance, index ->
            val interest = balance.multiply(periodicRate, MC).setScale(scale, RoundingMode.HALF_UP)
            val part = if (index == periods - 1) balance else principalPart.setScale(scale, RoundingMode.HALF_UP)
            part to interest
        }
    }

    fun american(
        principal: BigDecimal,
        periodicRate: BigDecimal,
        periods: Int,
        scale: Int = 2
    ): List<Installment> {
        requireInputs(principal, periodicRate, periods, scale)
        return buildSchedule(principal, periods, scale) { balance, index ->
            val interest = principal.multiply(periodicRate, MC).setScale(scale, RoundingMode.HALF_UP)
            val part = if (index == periods - 1) balance else BigDecimal.ZERO.setScale(scale)
            part to interest
        }
    }

    private fun requireInputs(p: BigDecimal, rate: BigDecimal, n: Int, scale: Int) {
    require(p > BigDecimal.ZERO) {
        "El principal debe ser mayor que cero"
    }
    require(rate >= BigDecimal.ZERO) {
        "La tasa no puede ser negativa"
    }
    require(n in 1..600) {
        "El número de períodos debe estar entre 1 y 600"
    }
    require(scale in 0..6) {
        "Escala monetaria no válida"
    }
}



       
    private fun buildSchedule(
        principal: BigDecimal,
        periods: Int,
        scale: Int,
        parts: (BigDecimal, Int) -> Pair<BigDecimal, BigDecimal>
    ): List<Installment> {
        var balance = principal.setScale(scale, RoundingMode.HALF_UP)

        return (0 until periods).map { index ->
            val (rawPrincipal, interest) = parts(balance, index)

            val principalPart = if (index == periods - 1) {
                balance
            } else {
                rawPrincipal.setScale(scale, RoundingMode.HALF_UP)
            }

            require(principalPart >= BigDecimal.ZERO && principalPart <= balance) {
                "Amortización inválida: revise tasa, plazo y redondeo"
            }

            val roundedInterest = interest.setScale(scale, RoundingMode.HALF_UP)
            val total = principalPart.add(roundedInterest).setScale(scale, RoundingMode.HALF_UP)

            balance = balance.subtract(principalPart).setScale(scale, RoundingMode.HALF_UP)

           Installment(index + 1, principalPart, roundedInterest, total)
        }
    }
}
