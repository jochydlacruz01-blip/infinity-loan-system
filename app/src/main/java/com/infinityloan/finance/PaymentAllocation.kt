package com.infinityloan.finance

/**
 * Distribuye un pago siguiendo este orden:
 * Mora → Cargos → Intereses → Capital.
 *
 * Todos los importes se expresan en unidades monetarias menores.
 */
object PaymentAllocation {

    data class Result(
        val lateFeeMinor: Long,
        val chargesMinor: Long,
        val interestMinor: Long,
        val principalMinor: Long,
        val unappliedMinor: Long
    )

    fun allocate(
        paymentMinor: Long,
        lateFeeDueMinor: Long,
        chargesDueMinor: Long,
        interestDueMinor: Long,
        principalDueMinor: Long
    ): Result {

        require(paymentMinor > 0L) {
            "El pago debe ser mayor que cero"
        }

        require(
            lateFeeDueMinor >= 0L &&
            chargesDueMinor >= 0L &&
            interestDueMinor >= 0L &&
            principalDueMinor >= 0L
        ) {
            "Los saldos pendientes no pueden ser negativos"
        }

        var remaining = paymentMinor

        val lateFee = minOf(remaining, lateFeeDueMinor)
        remaining -= lateFee

        val charges = minOf(remaining, chargesDueMinor)
        remaining -= charges

        val interest = minOf(remaining, interestDueMinor)
        remaining -= interest

        val principal = minOf(remaining, principalDueMinor)
        remaining -= principal

        return Result(
            lateFeeMinor = lateFee,
            chargesMinor = charges,
            interestMinor = interest,
            principalMinor = principal,
            unappliedMinor = remaining
        )
    }
}
