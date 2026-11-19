package com.infinityloan.finance

import java.time.LocalDate

/**
 * Distribuye un pago entre cuotas contractuales, desde la más antigua.
 * Dentro de cada cuota respeta el orden: mora, cargos, intereses y capital.
 *
 * Los importes se expresan en unidades monetarias menores (centavos).
 * No convierte el excedente en crédito: lo devuelve como unappliedMinor
 * para que la capa transaccional lo registre en el libro de crédito.
 */
object InstallmentPaymentAllocator {

    data class DueInstallment(
        val number: Int,
        val dueDate: LocalDate,
        val lateFeeDueMinor: Long = 0L,
        val chargesDueMinor: Long = 0L,
        val interestDueMinor: Long,
        val principalDueMinor: Long
    )

    data class InstallmentAllocation(
        val installmentNumber: Int,
        val lateFeePaidMinor: Long,
        val chargesPaidMinor: Long,
        val interestPaidMinor: Long,
        val principalPaidMinor: Long
    ) {
        val totalPaidMinor: Long
            get() = Math.addExact(
                Math.addExact(lateFeePaidMinor, chargesPaidMinor),
                Math.addExact(interestPaidMinor, principalPaidMinor)
            )
    }

    data class Result(
        val allocations: List<InstallmentAllocation>,
        val unappliedMinor: Long
    ) {
        val totalAllocatedMinor: Long
            get() = allocations.fold(0L) { total, allocation ->
                Math.addExact(total, allocation.totalPaidMinor)
            }
    }

    fun allocate(paymentMinor: Long, installments: List<DueInstallment>): Result {
        require(paymentMinor > 0L) { "El pago debe ser mayor que cero." }
        require(installments.map { it.number }.distinct().size == installments.size) {
            "No se permiten números de cuota duplicados."
        }
        installments.forEach {
            require(it.number > 0) { "El número de cuota debe ser positivo." }
            require(
                it.lateFeeDueMinor >= 0L &&
                it.chargesDueMinor >= 0L &&
                it.interestDueMinor >= 0L &&
                it.principalDueMinor >= 0L
            ) { "Los importes pendientes no pueden ser negativos." }
        }

        var remaining = paymentMinor
        val allocations = installments
            .sortedWith(compareBy<DueInstallment> { it.dueDate }.thenBy { it.number })
            .mapNotNull { installment ->
                if (remaining == 0L) return@mapNotNull null

                fun take(due: Long): Long {
                    val amount = minOf(remaining, due)
                    remaining -= amount
                    return amount
                }

                val lateFee = take(installment.lateFeeDueMinor)
                val charges = take(installment.chargesDueMinor)
                val interest = take(installment.interestDueMinor)
                val principal = take(installment.principalDueMinor)

                val installmentPaid = Math.addExact(
                    Math.addExact(lateFee, charges),
                    Math.addExact(interest, principal)
                )
                if (installmentPaid == 0L) null
                else InstallmentAllocation(
                    installmentNumber = installment.number,
                    lateFeePaidMinor = lateFee,
                    chargesPaidMinor = charges,
                    interestPaidMinor = interest,
                    principalPaidMinor = principal
                )
            }

        return Result(allocations, remaining)
    }
}
