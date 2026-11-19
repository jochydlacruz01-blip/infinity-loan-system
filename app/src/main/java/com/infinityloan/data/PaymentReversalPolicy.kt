package com.infinityloan.data

/**
 * Pure eligibility gate for a future, transactional payment reversal.
 *
 * This does not post accounting entries. The caller must still authenticate the
 * actor, require the reversal permission, and execute all ledger changes atomically.
 */
object PaymentReversalPolicy {
    enum class Result {
        ELIGIBLE,
        INVALID_PAYMENT_AMOUNT,
        INVALID_ACTOR,
        INVALID_REASON,
        INVALID_CREDIT_LEDGER,
        LATER_PAYMENT_EXISTS,
        GENERATED_CREDIT_ALREADY_USED,
        ORIGINAL_CASH_SESSION_NOT_OPEN
    }

    data class Request(
        val paymentAmountMinor: Long,
        val actorStaffId: String,
        val reason: String,
        val hasLaterPaymentsForLoan: Boolean,
        val generatedCreditMinor: Long,
        val availableCustomerCreditMinor: Long,
        val hasOriginalCashReceipt: Boolean,
        val originalCashSessionIsOpen: Boolean
    )

    fun evaluate(request: Request): Result {
        if (request.paymentAmountMinor <= 0L) return Result.INVALID_PAYMENT_AMOUNT
        if (request.actorStaffId.isBlank()) return Result.INVALID_ACTOR
        if (request.reason.isBlank()) return Result.INVALID_REASON
        if (request.generatedCreditMinor < 0L ||
            request.generatedCreditMinor > request.paymentAmountMinor ||
            request.availableCustomerCreditMinor < 0L
        ) return Result.INVALID_CREDIT_LEDGER
        if (request.hasLaterPaymentsForLoan) return Result.LATER_PAYMENT_EXISTS
        if (request.availableCustomerCreditMinor < request.generatedCreditMinor) {
            return Result.GENERATED_CREDIT_ALREADY_USED
        }
        if (request.hasOriginalCashReceipt && !request.originalCashSessionIsOpen) {
            return Result.ORIGINAL_CASH_SESSION_NOT_OPEN
        }
        return Result.ELIGIBLE
    }
}
