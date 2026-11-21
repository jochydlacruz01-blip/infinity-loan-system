package com.infinityloan.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentReversalPolicyTest {
    private fun request(
        paymentAmount: Long = 10_000L,
        actor: String = "STAFF-1",
        reason: String = "Corrección autorizada",
        laterPayment: Boolean = false,
        generatedCredit: Long = 0L,
        availableCredit: Long = 0L,
        cashReceipt: Boolean = false,
        cashSessionOpen: Boolean = false
    ) = PaymentReversalPolicy.Request(
        paymentAmountMinor = paymentAmount,
        actorStaffId = actor,
        reason = reason,
        hasLaterPaymentsForLoan = laterPayment,
        generatedCreditMinor = generatedCredit,
        availableCustomerCreditMinor = availableCredit,
        hasOriginalCashReceipt = cashReceipt,
        originalCashSessionIsOpen = cashSessionOpen
    )

    @Test fun acceptsValidNonCashReversalWithoutGeneratedCredit() {
        assertEquals(PaymentReversalPolicy.Result.ELIGIBLE,
            PaymentReversalPolicy.evaluate(request()))
    }

    @Test fun rejectsNonPositivePayment() {
        assertEquals(PaymentReversalPolicy.Result.INVALID_PAYMENT_AMOUNT,
            PaymentReversalPolicy.evaluate(request(paymentAmount = 0L)))
    }

    @Test fun requiresAnActorAndReason() {
        assertEquals(PaymentReversalPolicy.Result.INVALID_ACTOR,
            PaymentReversalPolicy.evaluate(request(actor = "  ")))
        assertEquals(PaymentReversalPolicy.Result.INVALID_REASON,
            PaymentReversalPolicy.evaluate(request(reason = "  ")))
    }

    @Test fun rejectsReversalReasonLongerThanPersistedLimit() {
        assertEquals(PaymentReversalPolicy.Result.INVALID_REASON,
            PaymentReversalPolicy.evaluate(request(reason = "x".repeat(501))))
        assertEquals(PaymentReversalPolicy.Result.ELIGIBLE,
            PaymentReversalPolicy.evaluate(request(reason = "x".repeat(500))))
    }

    @Test fun rejectsInconsistentCreditLedgerValues() {
        assertEquals(PaymentReversalPolicy.Result.INVALID_CREDIT_LEDGER,
            PaymentReversalPolicy.evaluate(request(generatedCredit = -1L)))
        assertEquals(PaymentReversalPolicy.Result.INVALID_CREDIT_LEDGER,
            PaymentReversalPolicy.evaluate(request(generatedCredit = 10_001L)))
        assertEquals(PaymentReversalPolicy.Result.INVALID_CREDIT_LEDGER,
            PaymentReversalPolicy.evaluate(request(availableCredit = -1L)))
    }

    @Test fun blocksWhenLaterPaymentsExist() {
        assertEquals(PaymentReversalPolicy.Result.LATER_PAYMENT_EXISTS,
            PaymentReversalPolicy.evaluate(request(laterPayment = true)))
    }

    @Test fun blocksWhenGeneratedCreditHasBeenConsumed() {
        assertEquals(PaymentReversalPolicy.Result.GENERATED_CREDIT_ALREADY_USED,
            PaymentReversalPolicy.evaluate(request(
                generatedCredit = 2_500L,
                availableCredit = 2_499L
            )))
    }

    @Test fun allowsReversalWhenGeneratedCreditRemainsAvailable() {
        assertEquals(PaymentReversalPolicy.Result.ELIGIBLE,
            PaymentReversalPolicy.evaluate(request(
                generatedCredit = 2_500L,
                availableCredit = 3_000L
            )))
    }

    @Test fun acceptsGeneratedCreditAtExactAvailableBalanceBoundary() {
        assertEquals(PaymentReversalPolicy.Result.ELIGIBLE,
            PaymentReversalPolicy.evaluate(request(
                generatedCredit = 2_500L,
                availableCredit = 2_500L
            )))
    }

    @Test fun rejectsWhitespaceOnlyActorAndTrimmedReasonOverLimit() {
        assertEquals(PaymentReversalPolicy.Result.INVALID_ACTOR,
            PaymentReversalPolicy.evaluate(request(actor = "\t  ")))
        assertEquals(PaymentReversalPolicy.Result.INVALID_REASON,
            PaymentReversalPolicy.evaluate(request(reason = " " + "x".repeat(501))))
    }

    @Test fun cashReversalRequiresTheOriginalSessionToRemainOpen() {
        assertEquals(PaymentReversalPolicy.Result.ORIGINAL_CASH_SESSION_NOT_OPEN,
            PaymentReversalPolicy.evaluate(request(cashReceipt = true)))
        assertEquals(PaymentReversalPolicy.Result.ELIGIBLE,
            PaymentReversalPolicy.evaluate(request(
                cashReceipt = true,
                cashSessionOpen = true
            )))
    }
}
