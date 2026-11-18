package com.infinityloan.data

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentDaoTest {

    private lateinit var database: InfinityDatabase

    @Before
    fun setup() {
        val context: Context =
            InstrumentationRegistry.getInstrumentation().targetContext

        database = Room.inMemoryDatabaseBuilder(
            context,
            InfinityDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun cleanup() {
        database.close()
    }

    @Test
    fun duplicatePaymentDoesNotReduceBalanceTwice() = runBlocking {

        val client = ClientEntity(
            fullName = "Cliente de prueba",
            documentNumber = "TEST-001",
            phone = "0000000000",
            address = "Prueba"
        )

        database.clients().insert(client)

        val loan = LoanEntity(
            clientId = client.id,
            principalMinor = 10000L,
            balanceMinor = 10000L,
            currency = "DOP",
            periodicRate = "0.05",
            periods = 12,
            method = "FRENCH"
        )

        database.loans().insert(loan)

        val payment = PaymentEntity(
            operationId = "PAYMENT-TEST-001",
            loanId = loan.id,
            amountMinor = 1000L,
            reference = "Prueba"
        )

        val firstResult =
            database.payments().postPayment(payment)

        val secondResult =
            database.payments().postPayment(payment)

        val updatedLoan =
            database.payments().findLoan(loan.id)

        assertEquals(PaymentPostResult.POSTED, firstResult)
        assertEquals(PaymentPostResult.DUPLICATE, secondResult)
        assertEquals(9000L, updatedLoan?.balanceMinor)
    }
        @Test
    fun invalidPaymentsDoNotChangeLoanBalance() = runBlocking {

        val client = ClientEntity(
            fullName = "Cliente de validación",
            documentNumber = "TEST-INVALID-001",
            phone = "0000000000",
            address = "Prueba"
        )

        database.clients().insert(client)

        val loan = LoanEntity(
            clientId = client.id,
            principalMinor = 10000L,
            balanceMinor = 10000L,
            currency = "DOP",
            periodicRate = "0.05",
            periods = 12,
            method = "FRENCH"
        )

        database.loans().insert(loan)

        val zeroPayment = PaymentEntity(
            operationId = "TEST-ZERO-001",
            loanId = loan.id,
            amountMinor = 0L,
            reference = "Prueba cero"
        )

        val negativePayment = PaymentEntity(
            operationId = "TEST-NEGATIVE-001",
            loanId = loan.id,
            amountMinor = -100L,
            reference = "Prueba negativa"
        )

        val excessivePayment = PaymentEntity(
            operationId = "TEST-EXCESSIVE-001",
            loanId = loan.id,
            amountMinor = 10001L,
            reference = "Prueba exceso"
        )

        assertEquals(
            PaymentPostResult.INVALID_AMOUNT,
            database.payments().postPayment(zeroPayment)
        )

        assertEquals(
            PaymentPostResult.INVALID_AMOUNT,
            database.payments().postPayment(negativePayment)
        )

        assertEquals(
            PaymentPostResult.EXCEEDS_BALANCE,
            database.payments().postPayment(excessivePayment)
        )

        val updatedLoan = database.payments().findLoan(loan.id)

        assertEquals(10000L, updatedLoan?.balanceMinor)
    }

    @Test
    fun paymentsForMissingOrInactiveLoansAreRejected() = runBlocking {

        val client = ClientEntity(
            fullName = "Cliente de estado",
            documentNumber = "TEST-STATUS-001",
            phone = "0000000000",
            address = "Prueba"
        )

        database.clients().insert(client)

        val closedLoan = LoanEntity(
            clientId = client.id,
            principalMinor = 10000L,
            balanceMinor = 8000L,
            currency = "DOP",
            periodicRate = "0.05",
            periods = 12,
            method = "FRENCH",
            status = "CLOSED"
        )

        database.loans().insert(closedLoan)

        val missingLoanPayment = PaymentEntity(
            operationId = "TEST-MISSING-001",
            loanId = "LOAN-NOT-FOUND",
            amountMinor = 1000L,
            reference = "Préstamo inexistente"
        )

        val closedLoanPayment = PaymentEntity(
            operationId = "TEST-CLOSED-001",
            loanId = closedLoan.id,
            amountMinor = 1000L,
            reference = "Préstamo cerrado"
        )

        assertEquals(
            PaymentPostResult.LOAN_NOT_FOUND,
            database.payments().postPayment(missingLoanPayment)
        )

        assertEquals(
            PaymentPostResult.LOAN_NOT_ACTIVE,
            database.payments().postPayment(closedLoanPayment)
        )

        val updatedLoan = database.payments().findLoan(closedLoan.id)

        assertEquals(8000L, updatedLoan?.balanceMinor)
        assertEquals(
            null,
            database.payments().findByOperationId("TEST-MISSING-001")
        )
        assertEquals(
            null,
            database.payments().findByOperationId("TEST-CLOSED-001")
        )
    }
}
