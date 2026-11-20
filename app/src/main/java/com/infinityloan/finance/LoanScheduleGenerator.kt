package com.infinityloan.finance

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Construye un calendario contractual a partir de un cálculo financiero ya validado.
 * La tasa recibida por LoanCalculator es la tasa efectiva de cada período de pago.
 */
object LoanScheduleGenerator {

    enum class Frequency {
        WEEKLY,
        BIWEEKLY,
        MONTHLY
    }

    data class ScheduledInstallment(
        val number: Int,
        val dueDate: LocalDate,
        val principal: BigDecimal,
        val interest: BigDecimal,
        val total: BigDecimal,
        val remainingPrincipal: BigDecimal
    )

    fun generate(
        principal: BigDecimal,
        periodicRate: BigDecimal,
        periods: Int,
        method: String,
        frequency: Frequency = Frequency.MONTHLY,
        firstDueDate: LocalDate,
        scale: Int = 2
    ): List<ScheduledInstallment> {
        require(periods in 1..600) { "El número de cuotas debe estar entre 1 y 600" }
        require(firstDueDate != LocalDate.MIN && firstDueDate != LocalDate.MAX) {
            "La fecha del primer vencimiento no es válida"
        }

        require(scale in 0..6) { "Escala monetaria no válida" }
        require(principal > BigDecimal.ZERO) { "El principal debe ser mayor que cero" }
        require(principal.setScale(scale, java.math.RoundingMode.HALF_UP) > BigDecimal.ZERO) {
            "El principal no puede redondearse a cero con la escala seleccionada"
        }
        require(periodicRate >= BigDecimal.ZERO) { "La tasa no puede ser negativa" }

        val installments = when (method.uppercase()) {
            "FRANCES", "FRENCH" -> LoanCalculator.french(principal, periodicRate, periods, scale)
            "ITALIANO" -> LoanCalculator.italian(principal, periodicRate, periods, scale)
            "AMERICANO" -> LoanCalculator.american(principal, periodicRate, periods, scale)
            else -> throw IllegalArgumentException("Método de amortización no válido: $method")
        }

        var remaining = principal.setScale(scale, java.math.RoundingMode.HALF_UP)
        val schedule = installments.mapIndexed { index, installment ->
            remaining = remaining.subtract(installment.principal)
                .setScale(scale, java.math.RoundingMode.HALF_UP)
            ScheduledInstallment(
                number = installment.number,
                dueDate = dueDate(firstDueDate, frequency, index),
                principal = installment.principal,
                interest = installment.interest,
                total = installment.total,
                remainingPrincipal = remaining
            )
        }

        require(schedule.all { row ->
            row.principal >= BigDecimal.ZERO && row.interest >= BigDecimal.ZERO &&
                row.total.compareTo(row.principal.add(row.interest)) == 0 &&
                row.remainingPrincipal >= BigDecimal.ZERO
        }) { "El calendario contiene importes o balances inválidos" }
        require(schedule.zipWithNext().all { (current, next) ->
            next.number == current.number + 1 && next.dueDate.isAfter(current.dueDate) &&
                next.remainingPrincipal <= current.remainingPrincipal
        }) { "El calendario no mantiene orden y balances descendentes" }
        require(schedule.last().remainingPrincipal.compareTo(BigDecimal.ZERO) == 0) {
            "El calendario no liquida el principal al finalizar"
        }
        return schedule
    }

    private fun dueDate(firstDueDate: LocalDate, frequency: Frequency, index: Int): LocalDate =
        when (frequency) {
            Frequency.WEEKLY -> firstDueDate.plusWeeks(index.toLong())
            Frequency.BIWEEKLY -> firstDueDate.plusWeeks(index.toLong() * 2L)
            Frequency.MONTHLY -> firstDueDate.plusMonths(index.toLong())
        }
}
