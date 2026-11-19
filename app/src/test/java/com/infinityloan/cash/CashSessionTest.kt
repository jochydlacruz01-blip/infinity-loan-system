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


class CashReportTest {
    @Test fun reportGroupsCurrenciesAndKeepsOpenExpectedCashSeparateFromCountedCash() {
        val open = CashSession("open", "staff", "DOP", 5_000L)
            .addMovement(CashMovement("in", CashMovementType.CASH_IN, 1_000L, "Cobro"))
        val closed = CashSession("closed", "staff", "DOP", 2_000L)
            .addMovement(CashMovement("out", CashMovementType.CASH_OUT, 500L, "Salida"))
            .close(1_400L)
        val usd = CashSession("usd", "staff", "USD", 900L).close(800L)

        val report = buildCashReport(listOf(open, closed, usd))
        val dop = report.first { it.currency == "DOP" }
        assertEquals(2, dop.sessionCount)
        assertEquals(1, dop.openSessionCount)
        assertEquals(1, dop.closedSessionCount)
        assertEquals(7_500L, dop.expectedCashMinor)
        assertEquals(6_000L, dop.expectedOpenCashMinor)
        assertEquals(1_500L, dop.expectedClosedCashMinor)
        assertEquals(1_400L, dop.countedCashMinor)
        assertEquals(-100L, dop.varianceMinor)
        assertEquals("USD", report.last().currency)
    }

    @Test fun emptyReportIsEmpty() { assertTrue(buildCashReport(emptyList()).isEmpty()) }

    @Test fun reportArithmeticOverflowFailsInsteadOfWrapping() {
        val first = CashSession("first", "staff", "USD", Long.MAX_VALUE)
        val second = CashSession("second", "staff", "USD", 1L)
        assertThrows(ArithmeticException::class.java) {
            buildCashReport(listOf(first, second))
        }
    }

    @Test fun closedSessionReportVarianceIsPositiveWhenOverCounted() {
        val closed = CashSession("S", "staff", "EUR", 1_000L).close(1_250L)
        val report = buildCashReport(listOf(closed)).single()
        assertEquals(250L, report.varianceMinor)
        assertEquals(1_250L, report.countedCashMinor)
    }

    @Test fun reportVarianceOnlyIncludesClosedSessions() {
        val closed = CashSession("closed", "staff", "DOP", 1_000L).close(900L)
        val open = CashSession("open", "staff", "DOP", 5_000L)
        val report = buildCashReport(listOf(closed, open)).single()
        assertEquals(-100L, report.varianceMinor)
        assertEquals(900L, report.countedCashMinor)
        assertEquals(6_000L, report.expectedCashMinor)
    }
}


class CashStaffReportTest {
    @Test fun reportSeparatesEmployeesAndCurrenciesAndOnlyCountsClosedVariances() {
        val staffAClosed = CashSession("a1", "staff-a", "DOP", 1_000L).close(900L)
        val staffAOpen = CashSession("a2", "staff-a", "DOP", 500L)
        val staffBClosed = CashSession("b1", "staff-b", "DOP", 2_000L).close(2_250L)
        val staffAEuro = CashSession("a3", "staff-a", "EUR", 700L).close(650L)
        val report = buildCashStaffReport(listOf(staffAClosed, staffAOpen, staffBClosed, staffAEuro))
        val aDop = report.first { it.staffId == "staff-a" && it.currency == "DOP" }
        assertEquals(2, aDop.sessionCount)
        assertEquals(1, aDop.openSessionCount)
        assertEquals(1, aDop.closedSessionCount)
        assertEquals(1_500L, aDop.expectedCashMinor)
        assertEquals(900L, aDop.countedCashMinor)
        assertEquals(-100L, aDop.varianceMinor)
        assertEquals(250L, report.first { it.staffId == "staff-b" }.varianceMinor)
        assertTrue(report.any { it.currency == "EUR" && it.staffId == "staff-a" })
    }

    @Test fun emptyStaffReportIsEmpty() { assertTrue(buildCashStaffReport(emptyList()).isEmpty()) }

    @Test fun staffReportUsesExactArithmetic() {
        val first = CashSession("one", "staff", "USD", Long.MAX_VALUE)
        val second = CashSession("two", "staff", "USD", 1L)
        assertThrows(ArithmeticException::class.java) { buildCashStaffReport(listOf(first, second)) }
    }
}
