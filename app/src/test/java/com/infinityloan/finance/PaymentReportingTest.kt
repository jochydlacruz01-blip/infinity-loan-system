package com.infinityloan.finance

import com.infinityloan.data.PaymentEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentReportingTest {
    @Test
    fun excludesReversedPaymentsButKeepsOtherPayments() {
        val payments = listOf(
            PaymentEntity("op-1", "loan-1", 12_500L, "R-1"),
            PaymentEntity("op-2", "loan-1", 8_000L, "R-2"),
            PaymentEntity("op-3", "loan-2", 5_000L, "R-3")
        )

        val result = PaymentReporting.effectivePayments(
            payments,
            setOf("op-2")
        )

        assertEquals(listOf("op-1", "op-3"), result.map { it.operationId })
        assertEquals(17_500L, result.sumOf { it.amountMinor })
    }

    @Test
    fun unknownReversalIdsDoNotChangeTotals() {
        val payments = listOf(
            PaymentEntity("op-1", "loan-1", 1_000L, "R-1")
        )

        assertEquals(
            payments,
            PaymentReporting.effectivePayments(payments, setOf("missing-operation"))
        )
    }

    @Test
    fun allReversedPaymentsProduceEmptyEffectiveHistory() {
        val payments = listOf(
            PaymentEntity("op-1", "loan-1", 1_000L, "R-1")
        )

        assertEquals(
            emptyList<PaymentEntity>(),
            PaymentReporting.effectivePayments(payments, setOf("op-1"))
        )
    }
}
