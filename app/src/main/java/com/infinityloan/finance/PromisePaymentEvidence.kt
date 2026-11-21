package com.infinityloan.finance

import java.time.LocalDate

enum class PromiseEvidenceState {
    PENDING, PARTIAL, KEPT, OVERDUE, BROKEN, CANCELLED
}

data class PromiseEvidenceInput(
    val promiseId: String,
    val promisedAmountMinor: Long,
    val promisedDate: LocalDate,
    val status: String,
    val linkedPaymentMinor: Long
)

data class PromiseEvidenceResult(
    val promiseId: String,
    val promisedAmountMinor: Long,
    val linkedPaymentMinor: Long,
    val remainingMinor: Long,
    val state: PromiseEvidenceState
)

/**
 * Promise fulfillment is based only on payment amounts explicitly linked to the
 * promise. Merely changing a promise status cannot manufacture payment evidence.
 */
object PromisePaymentEvidenceCalculator {
    fun evaluate(input: PromiseEvidenceInput, asOf: LocalDate): PromiseEvidenceResult {
        require(input.promiseId.isNotBlank())
        require(input.promisedAmountMinor > 0L)
        require(input.linkedPaymentMinor >= 0L)
        require(input.linkedPaymentMinor <= input.promisedAmountMinor) {
            "El importe vinculado supera el monto prometido."
        }
        val remaining = input.promisedAmountMinor - input.linkedPaymentMinor
        val state = when (input.status) {
            "CANCELLED" -> PromiseEvidenceState.CANCELLED
            "BROKEN" -> PromiseEvidenceState.BROKEN
            "KEPT" -> if (remaining == 0L) PromiseEvidenceState.KEPT else PromiseEvidenceState.UNVERIFIED
            "OPEN" -> when {
                remaining == 0L -> PromiseEvidenceState.KEPT
                input.linkedPaymentMinor > 0L -> PromiseEvidenceState.PARTIAL
                input.promisedDate.isBefore(asOf) -> PromiseEvidenceState.OVERDUE
                else -> PromiseEvidenceState.PENDING
            }
            else -> throw IllegalArgumentException("Estado de promesa desconocido.")
        }
        return PromiseEvidenceResult(
            promiseId = input.promiseId,
            promisedAmountMinor = input.promisedAmountMinor,
            linkedPaymentMinor = input.linkedPaymentMinor,
            remainingMinor = remaining,
            state = state
        )
    }
}
