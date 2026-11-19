package com.infinityloan.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CashPaymentIntegrationTest {
    private lateinit var database: InfinityDatabase

    @Before
    fun setup() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, InfinityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun cleanup() {
        database.close()
    }

    private suspend fun createDueLoan(): LoanEntity {
        val client = ClientEntity(
            fullName = "Cliente integración caja",
            documentNumber = "CASH-INTEGRATION-001",
            phone = "0000000000",
            address = "Prueba"
        )
        database.clients().insert(client)
        val loan = LoanEntity(
            id = "CASH-LOAN-001",
            clientId = client.id,
            principalMinor = 10000L,
            balanceMinor = 10000L,
            currency = "DOP",
            periodicRate = "0.00",
            periods = 1,
            method = "FRANCES",
            firstDueDate = LocalDate.now().minusDays(1).toString()
        )
        database.loans().insert(loan)
        database.installments().insertAll(
            listOf(
                LoanInstallmentEntity(
                    loanId = loan.id,
                    number = 1,
                    dueDate = LocalDate.now().minusDays(1).toString(),
                    principalMinor = 10000L,
                    interestMinor = 0L,
                    totalMinor = 10000L,
                    remainingPrincipalMinor = 0L
                )
            )
        )
        return loan
    }

    @Test
    fun cashPaymentPostsLoanAndCashMovementAtomically() = runBlocking {
        val loan = createDueLoan()
        database.openCashSession(
            CashSessionEntity(
                sessionId = "CASH-SESSION-001",
                staffId = "CASHIER-001",
                currency = "DOP",
                openingMinor = 5000L,
                openedAt = 1L
            )
        )
        val payment = PaymentEntity(
            operationId = "CASH-PAYMENT-001",
            loanId = loan.id,
            amountMinor = 3000L,
            reference = "Cobro de prueba",
            createdAt = 2L
        )

        assertEquals(
            PaymentPostResult.POSTED,
            database.postCashLoanPayment(payment, "CASH-SESSION-001")
        )
        assertEquals(
            PaymentPostResult.DUPLICATE,
            database.postCashLoanPayment(payment, "CASH-SESSION-001")
        )
        assertEquals(7000L, database.loans().findById(loan.id)?.balanceMinor)
        assertEquals(payment.operationId, database.payments().findByOperationId(payment.operationId)?.operationId)
        val movements = database.cashSessions().findMovements("CASH-SESSION-001")
        assertEquals(1, movements.size)
        assertEquals("CASH-PAY-CASH-PAYMENT-001", movements.single().operationId)
        assertEquals(3000L, movements.single().amountMinor)
        assertEquals("CASH_IN", movements.single().type)
    }

    @Test
    fun cashPaymentRejectsClosedOrWrongCurrencySessionWithoutPosting() = runBlocking {
        val loan = createDueLoan()
        database.openCashSession(
            CashSessionEntity(
                sessionId = "CASH-SESSION-USD",
                staffId = "CASHIER-USD",
                currency = "USD",
                openingMinor = 0L,
                openedAt = 1L
            )
        )
        val payment = PaymentEntity(
            operationId = "CASH-PAYMENT-WRONG-CURRENCY",
            loanId = loan.id,
            amountMinor = 1000L,
            reference = "Moneda incorrecta"
        )

        assertEquals(
            PaymentPostResult.INVALID_SCHEDULE,
            database.postCashLoanPayment(payment, "CASH-SESSION-USD")
        )
        assertNull(database.payments().findByOperationId(payment.operationId))
        assertEquals(10000L, database.loans().findById(loan.id)?.balanceMinor)
        assertEquals(0, database.cashSessions().findMovements("CASH-SESSION-USD").size)
    }

    @Test
    fun duplicateCashMovementRollsBackTheLoanPayment() = runBlocking {
        val loan = createDueLoan()
        database.openCashSession(
            CashSessionEntity(
                sessionId = "CASH-SESSION-ROLLBACK",
                staffId = "CASHIER-ROLLBACK",
                currency = "DOP",
                openingMinor = 0L,
                openedAt = 1L
            )
        )
        database.openCashSession(
            CashSessionEntity(
                sessionId = "CASH-SESSION-OTHER",
                staffId = "CASHIER-OTHER",
                currency = "DOP",
                openingMinor = 0L,
                openedAt = 2L
            )
        )
        val payment = PaymentEntity(
            operationId = "CASH-PAYMENT-ROLLBACK",
            loanId = loan.id,
            amountMinor = 2500L,
            reference = "Prueba de reversión",
            createdAt = 3L
        )
        database.cashSessions().insertMovement(
            CashMovementEntity(
                operationId = "CASH-PAY-CASH-PAYMENT-ROLLBACK",
                sessionId = "CASH-SESSION-OTHER",
                type = "CASH_IN",
                amountMinor = 2500L,
                reason = "Identificador ocupado para probar rollback",
                createdAt = 2L
            )
        )

        var rolledBack = false
        try {
            database.postCashLoanPayment(payment, "CASH-SESSION-ROLLBACK")
        } catch (_: IllegalStateException) {
            rolledBack = true
        }

        assertEquals(true, rolledBack)
        assertNull(database.payments().findByOperationId(payment.operationId))
        assertEquals(10000L, database.loans().findById(loan.id)?.balanceMinor)
        assertEquals(0, database.cashSessions().findMovements("CASH-SESSION-ROLLBACK").size)
        assertEquals(
            "CASH-PAY-CASH-PAYMENT-ROLLBACK",
            database.cashSessions().findMovementByOperation("CASH-PAY-CASH-PAYMENT-ROLLBACK")?.operationId
        )
    }

    @Test
    fun manualCashMovementIsIdempotentAndCannotOverdrawOrPostAfterClose() = runBlocking {
        database.openCashSession(
            CashSessionEntity(
                sessionId = "CASH-SESSION-MANUAL",
                staffId = "CASHIER-MANUAL",
                currency = "DOP",
                openingMinor = 1000L,
                openedAt = 1L
            )
        )

        val overdraft = CashMovementEntity(
            operationId = "MANUAL-OVERDRAFT",
            sessionId = "CASH-SESSION-MANUAL",
            type = "CASH_OUT",
            amountMinor = 1001L,
            reason = "No debe superar el efectivo disponible",
            createdAt = 2L
        )
        var rejectedOverdraft = false
        try {
            database.postCashMovement(overdraft)
        } catch (_: IllegalArgumentException) {
            rejectedOverdraft = true
        }
        assertEquals(true, rejectedOverdraft)
        assertEquals(0, database.cashSessions().findMovements("CASH-SESSION-MANUAL").size)

        val validOut = overdraft.copy(
            operationId = "MANUAL-VALID-OUT",
            amountMinor = 1000L,
            reason = "Salida dentro del efectivo disponible"
        )
        database.postCashMovement(validOut)
        var rejectedDuplicate = false
        try {
            database.postCashMovement(validOut)
        } catch (_: IllegalStateException) {
            rejectedDuplicate = true
        }
        assertEquals(true, rejectedDuplicate)
        assertEquals(1, database.cashSessions().findMovements("CASH-SESSION-MANUAL").size)

        database.closeCashSession("CASH-SESSION-MANUAL", countedMinor = 0L, closedAt = 3L)
        var rejectedAfterClose = false
        try {
            database.postCashMovement(
                validOut.copy(
                    operationId = "MANUAL-AFTER-CLOSE",
                    type = "CASH_IN",
                    amountMinor = 100L,
                    reason = "No debe registrarse después del cierre"
                )
            )
        } catch (_: IllegalStateException) {
            rejectedAfterClose = true
        }
        assertEquals(true, rejectedAfterClose)
        assertEquals(1, database.cashSessions().findMovements("CASH-SESSION-MANUAL").size)
    }



    @Test
    fun closingCashSessionPersistsPhysicalCountAndVarianceInputs() = runBlocking {
        database.openCashSession(
            CashSessionEntity(
                sessionId = "CASH-SESSION-CLOSE-AUDIT",
                staffId = "CASHIER-CLOSE",
                currency = "DOP",
                openingMinor = 5000L,
                openedAt = 10L
            )
        )
        database.postCashMovement(
            CashMovementEntity(
                operationId = "CASH-CLOSE-IN-001",
                sessionId = "CASH-SESSION-CLOSE-AUDIT",
                type = "CASH_IN",
                amountMinor = 1500L,
                reason = "Cobro de prueba",
                createdAt = 11L
            )
        )

        database.closeCashSession(
            sessionId = "CASH-SESSION-CLOSE-AUDIT",
            countedMinor = 6300L,
            closedAt = 12L
        )

        val closed = database.cashSessions().findSession("CASH-SESSION-CLOSE-AUDIT")
        assertEquals(12L, closed?.closedAt)
        assertEquals(6300L, closed?.countedClosingMinor)
        assertEquals(1, database.cashSessions().findMovements("CASH-SESSION-CLOSE-AUDIT").size)
    }

    @Test
    fun employeeCannotOpenTwoCashSessionsForSameCurrency() = runBlocking {
        database.openCashSession(
            CashSessionEntity(
                sessionId = "CASH-SESSION-UNIQUE-1",
                staffId = "CASHIER-UNIQUE",
                currency = "USD",
                openingMinor = 1000L,
                openedAt = 20L
            )
        )

        var rejected = false
        try {
            database.openCashSession(
                CashSessionEntity(
                    sessionId = "CASH-SESSION-UNIQUE-2",
                    staffId = "CASHIER-UNIQUE",
                    currency = "USD",
                    openingMinor = 500L,
                    openedAt = 21L
                )
            )
        } catch (_: IllegalStateException) {
            rejected = true
        }

        assertEquals(true, rejected)
        assertNull(database.cashSessions().findSession("CASH-SESSION-UNIQUE-2"))
        assertEquals(
            "CASH-SESSION-UNIQUE-1",
            database.cashSessions().findOpenSession("CASHIER-UNIQUE", "USD")?.sessionId
        )
    }

}
