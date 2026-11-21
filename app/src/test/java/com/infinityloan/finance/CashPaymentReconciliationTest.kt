package com.infinityloan.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CashPaymentReconciliationTest {
    @Test fun cleanPaymentAndCashMovementProduceNoIssues() {
        assertTrue(CashPaymentReconciliation.reconcile(
            listOf(ReconciliationPayment("p-1", 1200, "DOP")),
            listOf(ReconciliationCashMovement("c-1", "p-1", 1200, "DOP", "IN"))
        ).isEmpty())
    }

    @Test fun detectsMissingMovementAndAmountCurrencyMismatch() {
        val issues = CashPaymentReconciliation.reconcile(
            listOf(ReconciliationPayment("p-1", 1200, "DOP"), ReconciliationPayment("p-2", 900, "USD")),
            listOf(ReconciliationCashMovement("c-2", "p-2", 800, "EUR", "IN"))
        )
        assertEquals(1, issues.count { it.type == CashReconciliationIssueType.MISSING_CASH_MOVEMENT })
        assertEquals(1, issues.count { it.type == CashReconciliationIssueType.AMOUNT_MISMATCH })
        assertEquals(1, issues.count { it.type == CashReconciliationIssueType.CURRENCY_MISMATCH })
    }

    @Test fun detectsDuplicateAndOrphanMovements() {
        val issues = CashPaymentReconciliation.reconcile(
            listOf(ReconciliationPayment("p-1", 1000, "DOP")),
            listOf(
                ReconciliationCashMovement("c-1", "p-1", 1000, "DOP", "IN"),
                ReconciliationCashMovement("c-1-copy", "p-1", 1000, "DOP", "IN"),
                ReconciliationCashMovement("c-orphan", "p-unknown", 400, "DOP", "IN")
            )
        )
        assertEquals(1, issues.count { it.type == CashReconciliationIssueType.DUPLICATE_CASH_MOVEMENT })
        assertEquals(1, issues.count { it.type == CashReconciliationIssueType.ORPHAN_CASH_MOVEMENT })
    }

    @Test fun invalidPaymentIsReportedAndOutputIsDeterministic() {
        val payments = listOf(ReconciliationPayment("", 0, ""))
        val first = CashPaymentReconciliation.reconcile(payments, emptyList())
        val second = CashPaymentReconciliation.reconcile(payments, emptyList())
        assertEquals(first, second)
        assertEquals(CashReconciliationIssueType.INVALID_PAYMENT, first.single().type)
    }
    @Test fun detectsDuplicatePaymentIdsAndInvalidMovementRecords() {
        val issues = CashPaymentReconciliation.reconcile(
            listOf(
                ReconciliationPayment("p-1", 1000, "DOP"),
                ReconciliationPayment("p-1", 1000, "DOP")
            ),
            listOf(ReconciliationCashMovement(" ", "p-1", 0, "", ""))
        )
        assertEquals(1, issues.count { it.type == CashReconciliationIssueType.DUPLICATE_PAYMENT_OPERATION })
        assertEquals(1, issues.count { it.type == CashReconciliationIssueType.INVALID_CASH_MOVEMENT })
    }

    @Test fun flagsUnsupportedCurrenciesForPaymentsAndMovements() {
        val issues = CashPaymentReconciliation.reconcile(
            listOf(ReconciliationPayment("p-invalid-currency", 500, "XYZ")),
            listOf(ReconciliationCashMovement("c-invalid-currency", "p-invalid-currency", 500, "XYZ", "IN"))
        )
        assertEquals(1, issues.count { it.type == CashReconciliationIssueType.INVALID_PAYMENT })
        assertEquals(1, issues.count { it.type == CashReconciliationIssueType.INVALID_CASH_MOVEMENT })
    }

    @Test fun acceptsSupportedCurrencies() {
        listOf("DOP", "USD", "EUR").forEach { currency ->
            assertTrue(CashPaymentReconciliation.reconcile(
                listOf(ReconciliationPayment("p-$currency", 100, currency)),
                listOf(ReconciliationCashMovement("c-$currency", "p-$currency", 100, currency, "IN"))
            ).isEmpty())
        }
    }


    @Test fun detectsDuplicateMovementIdsAndUnlinkedMovements() {
        val issues = CashPaymentReconciliation.reconcile(
            listOf(ReconciliationPayment("p-1", 500, "DOP")),
            listOf(
                ReconciliationCashMovement("c-1", "p-1", 500, "DOP", "IN"),
                ReconciliationCashMovement("c-1", "p-1", 500, "DOP", "IN"),
                ReconciliationCashMovement("unlinked", null, 200, "DOP", "IN")
            )
        )
        assertEquals(1, issues.count { it.type == CashReconciliationIssueType.DUPLICATE_CASH_MOVEMENT_OPERATION_ID })
        assertEquals(1, issues.count { it.type == CashReconciliationIssueType.ORPHAN_CASH_MOVEMENT })
    }
}
