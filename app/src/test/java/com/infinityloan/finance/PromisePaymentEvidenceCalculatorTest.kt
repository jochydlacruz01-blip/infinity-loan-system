package com.infinityloan.finance

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PromisePaymentEvidenceCalculatorTest {
    private val today = LocalDate.parse("2026-11-20")

    private fun input(
        amount: Long = 10_000L,
        linked: Long = 0L,
        date: String = "2026-11-20",
        status: String = "OPEN"
    ) = PromiseEvidenceInput("promise-1", amount, LocalDate.parse(date), status, linked)

    @Test fun unpaidFuturePromiseIsPending() {
        assertEquals(PromiseEvidenceState.PENDING,
            PromisePaymentEvidenceCalculator.evaluate(input(), today).state)
    }

    @Test fun overdueUnpaidPromiseIsOverdue() {
        assertEquals(PromiseEvidenceState.OVERDUE,
            PromisePaymentEvidenceCalculator.evaluate(input(date = "2026-11-19"), today).state)
    }

    @Test fun partialLinkedPaymentRemainsPartial() {
        val result = PromisePaymentEvidenceCalculator.evaluate(input(linked = 4_000L), today)
        assertEquals(PromiseEvidenceState.PARTIAL, result.state)
        assertEquals(6_000L, result.remainingMinor)
    }

    @Test fun fullyLinkedPaymentKeepsPromise() {
        val result = PromisePaymentEvidenceCalculator.evaluate(input(linked = 10_000L), today)
        assertEquals(PromiseEvidenceState.KEPT, result.state)
        assertEquals(0L, result.remainingMinor)
    }

    @Test fun historicalKeptWithoutFullEvidenceIsMarkedUnverified() {
        assertEquals(PromiseEvidenceState.UNVERIFIED,
            PromisePaymentEvidenceCalculator.evaluate(input(status = "KEPT"), today).state)
    }

    @Test fun linkedAmountCannotExceedPromise() {
        assertThrows(IllegalArgumentException::class.java) {
            PromisePaymentEvidenceCalculator.evaluate(input(linked = 10_001L), today)
        }
    }

    @Test fun cancelledAndBrokenRemainClosed() {
        assertEquals(PromiseEvidenceState.CANCELLED,
            PromisePaymentEvidenceCalculator.evaluate(input(status = "CANCELLED"), today).state)
        assertEquals(PromiseEvidenceState.BROKEN,
            PromisePaymentEvidenceCalculator.evaluate(input(status = "BROKEN"), today).state)
    }
}
