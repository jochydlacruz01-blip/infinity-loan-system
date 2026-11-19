package com.infinityloan.cash

import org.junit.Assert.*
import org.junit.Test

class CashSessionTest {
    private fun session() = CashSession("S-1", "staff-1", "DOP", 10_000L)

    @Test fun cashInAndOutComputeExpectedBalance() {
        val result = session()
            .addMovement(CashMovement("in-1", CashMovementType.CASH_IN, 2_000L, "Cobro"))
            .addMovement(CashMovement("out-1", CashMovementType.CASH_OUT, 500L, "Compra"))
        assertEquals(11_500L, result.expectedCashMinor)
    }

    @Test fun closingCapturesVarianceWithoutChangingExpectedCash() {
        val closed = session()
            .addMovement(CashMovement("in-1", CashMovementType.CASH_IN, 2_000L, "Cobro"))
            .close(11_800L)
        assertTrue(closed.closed)
        assertEquals(12_000L, closed.expectedCashMinor)
        assertEquals(-200L, closed.varianceMinor)
    }

    @Test fun cannotPostAfterCloseOrCloseTwice() {
        val closed = session().close(10_000L)
        assertThrows(IllegalStateException::class.java) {
            closed.addMovement(CashMovement("in-1", CashMovementType.CASH_IN, 100L, "Cobro"))
        }
        assertThrows(IllegalStateException::class.java) { closed.close(10_000L) }
    }

    @Test fun rejectsDuplicateOperationsAndInvalidAmounts() {
        val initial = session().addMovement(CashMovement("x", CashMovementType.CASH_IN, 1L, "Ingreso"))
        assertThrows(IllegalArgumentException::class.java) {
            initial.addMovement(CashMovement("x", CashMovementType.CASH_OUT, 1L, "Duplicado"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CashMovement("bad", CashMovementType.CASH_IN, 0L, "Inválido")
        }
    }

    @Test fun cashOutCannotExceedAvailableBalance() {
        assertThrows(IllegalArgumentException::class.java) {
            session().addMovement(CashMovement("out", CashMovementType.CASH_OUT, 10_001L, "Egreso"))
        }
    }

    @Test fun closureStateMustMatchPhysicalCount() {
        assertThrows(IllegalArgumentException::class.java) {
            CashSession("S", "staff", "DOP", 0L, closed = false, countedClosingMinor = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CashSession("S", "staff", "DOP", 0L, closed = true)
        }
    }

    @Test fun arithmeticOverflowFailsExplicitly() {
        val initial = CashSession("S", "staff", "USD", Long.MAX_VALUE)
        assertThrows(ArithmeticException::class.java) {
            initial.addMovement(CashMovement("x", CashMovementType.CASH_IN, 1L, "Overflow")).expectedCashMinor
        }
    }
    @Test fun sessionRejectsUnsupportedCurrencyAndNegativeOpeningBalance() {
        assertThrows(IllegalArgumentException::class.java) {
            CashSession("S", "staff", "GBP", 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CashSession("S", "staff", "DOP", -1L)
        }
    }

    @Test fun sessionRejectsBlankIdentifiers() {
        assertThrows(IllegalArgumentException::class.java) {
            CashSession("", "staff", "DOP", 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CashSession("S", "", "DOP", 0L)
        }
    }

    @Test fun movementRequiresDescriptionAndOperationId() {
        assertThrows(IllegalArgumentException::class.java) {
            CashMovement("", CashMovementType.CASH_IN, 100L, "Ingreso")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CashMovement("x", CashMovementType.CASH_IN, 100L, " ")
        }
    }

    @Test fun varianceHandlesExtremeButRepresentableBalances() {
        val closed = CashSession("S", "staff", "DOP", Long.MAX_VALUE).close(0L)
        assertEquals(-Long.MAX_VALUE, closed.varianceMinor)
    }

    @Test fun cashOutExactlyAvailableBalanceIsAllowed() {
        val result = session().addMovement(
            CashMovement("out", CashMovementType.CASH_OUT, 10_000L, "Salida")
        )
        assertEquals(0L, result.expectedCashMinor)
    }

}
