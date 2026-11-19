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
}
