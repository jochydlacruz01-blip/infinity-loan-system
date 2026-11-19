package com.infinityloan.data

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PaymentDaoTest {

    private lateinit var database: InfinityDatabase

    private suspend fun insertLoanWithDueInstallment(loan: LoanEntity) {
        database.loans().insert(loan)
        val principalDue = loan.balanceMinor
        database.installments().insertAll(
            listOf(LoanInstallmentEntity(
                loanId = loan.id, number = 1,
                dueDate = LocalDate.now().minusDays(1).toString(),
                principalMinor = principalDue, interestMinor = 0L,
                totalMinor = principalDue, remainingPrincipalMinor = 0L
            ))
        )
    }

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

        insertLoanWithDueInstallment(loan)

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

        insertLoanWithDueInstallment(loan)

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

        assertEquals(PaymentPostResult.POSTED, database.payments().postPayment(excessivePayment))
        val updatedLoan = database.payments().findLoan(loan.id)
        val excessCredit = database.customerCredits().getBalance(client.id, "DOP")
        assertEquals(0L, updatedLoan?.balanceMinor)
        assertEquals(1L, excessCredit)
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

        insertLoanWithDueInstallment(closedLoan)

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
    @Test
    fun simultaneousPaymentsCannotExceedLoanBalance() = runBlocking {

        val client = ClientEntity(
            fullName = "Cliente de concurrencia",
            documentNumber = "TEST-CONCURRENT-001",
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

        insertLoanWithDueInstallment(loan)

        val payment1 = PaymentEntity(
            operationId = "TEST-CONCURRENT-001",
            loanId = loan.id,
            amountMinor = 7000L,
            reference = "Pago simultáneo 1"
        )

        val payment2 = PaymentEntity(
            operationId = "TEST-CONCURRENT-002",
            loanId = loan.id,
            amountMinor = 7000L,
            reference = "Pago simultáneo 2"
        )

        val results = listOf(
            async(Dispatchers.IO) {
                database.payments().postPayment(payment1)
            },
            async(Dispatchers.IO) {
                database.payments().postPayment(payment2)
            }
        ).awaitAll()

        val updatedLoan = database.payments().findLoan(loan.id)

        assertEquals(2, results.count { it == PaymentPostResult.POSTED })
        assertEquals(0, results.count { it == PaymentPostResult.EXCEEDS_BALANCE })
        assertEquals(0L, updatedLoan?.balanceMinor)
        assertEquals(4000L, database.customerCredits().getBalance(client.id, "DOP"))
    }
        @Test
    fun customerCreditPaymentUpdatesLoanAndCreditAtomically() = runBlocking {

        val client = ClientEntity(
            fullName = "Cliente crédito seguro",
            documentNumber = "CREDIT-SAFE-001",
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

        insertLoanWithDueInstallment(loan)

        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                operationId = "CREDIT-SAFE-001",
                clientId = client.id,
                loanId = loan.id,
                currency = "DOP",
                amountMinor = 5000L,
                reference = "Crédito inicial"
            )
        )

        val payment = PaymentEntity(
            operationId = "PAYMENT-CREDIT-SAFE-001",
            loanId = loan.id,
            amountMinor = 3000L,
            reference = "Pago con crédito"
        )

        val result = database.postPaymentWithCustomerCredit(payment)

        val updatedLoan = database.loans().findById(loan.id)

        val creditBalance = database.customerCredits()
            .getBalance(client.id, "DOP")

        assertEquals(CreditPaymentResult.POSTED, result)
        assertEquals(7000L, updatedLoan?.balanceMinor)
        assertEquals(2000L, creditBalance)

        assertEquals(
            payment.operationId,
            database.payments().findByOperationId(payment.operationId)?.operationId
        )
    }
        @Test
    fun insufficientCustomerCreditDoesNotChangeLoanOrCreatePayment() = runBlocking {

        val client = ClientEntity(
            fullName = "Cliente crédito insuficiente",
            documentNumber = "CREDIT-LOW-001",
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

        insertLoanWithDueInstallment(loan)

        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                operationId = "CREDIT-LOW-001",
                clientId = client.id,
                loanId = loan.id,
                currency = "DOP",
                amountMinor = 1000L,
                reference = "Crédito disponible"
            )
        )

        val payment = PaymentEntity(
            operationId = "PAYMENT-LOW-001",
            loanId = loan.id,
            amountMinor = 3000L,
            reference = "Intento de pago"
        )

        val result = database.postPaymentWithCustomerCredit(payment)

        val updatedLoan = database.loans().findById(loan.id)

        val creditBalance = database.customerCredits()
            .getBalance(client.id, "DOP")

        assertEquals(
            CreditPaymentResult.INSUFFICIENT_CREDIT,
            result
        )

        assertEquals(10000L, updatedLoan?.balanceMinor)
        assertEquals(1000L, creditBalance)

        assertEquals(
            null,
            database.payments().findByOperationId(payment.operationId)
        )
    }
        @Test
    fun duplicateCustomerCreditPaymentDoesNotDeductCreditTwice() = runBlocking {

        val client = ClientEntity(
            fullName = "Cliente crédito duplicado",
            documentNumber = "CREDIT-DUP-001",
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

        insertLoanWithDueInstallment(loan)

        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                operationId = "CREDIT-DUP-001",
                clientId = client.id,
                loanId = loan.id,
                currency = "DOP",
                amountMinor = 5000L,
                reference = "Crédito inicial"
            )
        )

        val payment = PaymentEntity(
            operationId = "PAYMENT-DUP-CREDIT-001",
            loanId = loan.id,
            amountMinor = 3000L,
            reference = "Pago duplicado"
        )

        val firstResult =
            database.postPaymentWithCustomerCredit(payment)

        val secondResult =
            database.postPaymentWithCustomerCredit(payment)

        val updatedLoan =
            database.loans().findById(loan.id)

        val creditBalance =
            database.customerCredits().getBalance(client.id, "DOP")

        assertEquals(CreditPaymentResult.POSTED, firstResult)
        assertEquals(CreditPaymentResult.DUPLICATE, secondResult)
        assertEquals(7000L, updatedLoan?.balanceMinor)
        assertEquals(2000L, creditBalance)
    }
        @Test
    fun customerCreditCannotPayLoanInDifferentCurrency() = runBlocking {

        val client = ClientEntity(
            fullName = "Cliente monedas diferentes",
            documentNumber = "CREDIT-CURRENCY-001",
            phone = "0000000000",
            address = "Prueba"
        )

        database.clients().insert(client)

        val loan = LoanEntity(
            clientId = client.id,
            principalMinor = 10000L,
            balanceMinor = 10000L,
            currency = "USD",
            periodicRate = "0.05",
            periods = 12,
            method = "FRENCH"
        )

        insertLoanWithDueInstallment(loan)

        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                operationId = "CREDIT-DOP-001",
                clientId = client.id,
                loanId = loan.id,
                currency = "DOP",
                amountMinor = 5000L,
                reference = "Crédito en pesos"
            )
        )

        val payment = PaymentEntity(
            operationId = "PAYMENT-CURRENCY-001",
            loanId = loan.id,
            amountMinor = 3000L,
            reference = "Intento de pago en USD"
        )

        val result =
            database.postPaymentWithCustomerCredit(payment)

        val updatedLoan =
            database.loans().findById(loan.id)

        val creditBalance =
            database.customerCredits().getBalance(client.id, "DOP")

        assertEquals(
            CreditPaymentResult.INSUFFICIENT_CREDIT,
            result
        )

        assertEquals(10000L, updatedLoan?.balanceMinor)
        assertEquals(5000L, creditBalance)

        assertEquals(
            null,
            database.payments().findByOperationId(payment.operationId)
        )
    }
        @Test
    fun customerCreditCannotPayClosedLoan() = runBlocking {

        val client = ClientEntity(
            fullName = "Cliente préstamo cerrado",
            documentNumber = "CREDIT-CLOSED-001",
            phone = "0000000000",
            address = "Prueba"
        )

        database.clients().insert(client)

        val loan = LoanEntity(
            clientId = client.id,
            principalMinor = 10000L,
            balanceMinor = 8000L,
            currency = "DOP",
            periodicRate = "0.05",
            periods = 12,
            method = "FRENCH",
            status = "CLOSED"
        )

        insertLoanWithDueInstallment(loan)

        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                operationId = "CREDIT-CLOSED-001",
                clientId = client.id,
                loanId = loan.id,
                currency = "DOP",
                amountMinor = 5000L,
                reference = "Crédito disponible"
            )
        )

        val payment = PaymentEntity(
            operationId = "PAYMENT-CLOSED-001",
            loanId = loan.id,
            amountMinor = 3000L,
            reference = "Intento de pago"
        )

        val result =
            database.postPaymentWithCustomerCredit(payment)

        val updatedLoan =
            database.loans().findById(loan.id)

        val creditBalance =
            database.customerCredits().getBalance(client.id, "DOP")

        assertEquals(
            CreditPaymentResult.LOAN_NOT_ACTIVE,
            result
        )

        assertEquals(8000L, updatedLoan?.balanceMinor)
        assertEquals(5000L, creditBalance)

        assertEquals(
            null,
            database.payments().findByOperationId(payment.operationId)
        )
    }
        @Test
    fun customerCreditPaymentCannotExceedLoanBalance() = runBlocking {

        val client = ClientEntity(
            fullName = "Cliente exceso de crédito",
            documentNumber = "CREDIT-EXCESS-001",
            phone = "0000000000",
            address = "Prueba"
        )

        database.clients().insert(client)

        val loan = LoanEntity(
            clientId = client.id,
            principalMinor = 10000L,
            balanceMinor = 2000L,
            currency = "DOP",
            periodicRate = "0.05",
            periods = 12,
            method = "FRENCH"
        )

        insertLoanWithDueInstallment(loan)

        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                operationId = "CREDIT-EXCESS-001",
                clientId = client.id,
                loanId = loan.id,
                currency = "DOP",
                amountMinor = 5000L,
                reference = "Crédito disponible"
            )
        )

        val payment = PaymentEntity(
            operationId = "PAYMENT-EXCESS-CREDIT-001",
            loanId = loan.id,
            amountMinor = 3000L,
            reference = "Intento de pago excesivo"
        )

        val result =
            database.postPaymentWithCustomerCredit(payment)

        val updatedLoan =
            database.loans().findById(loan.id)

        val creditBalance =
            database.customerCredits().getBalance(client.id, "DOP")

        assertEquals(CreditPaymentResult.POSTED, result)
        assertEquals(0L, updatedLoan?.balanceMinor)
        assertEquals(3000L, creditBalance)
        assertEquals(payment.operationId, database.payments().findByOperationId(payment.operationId)?.operationId)
    }
    @Test
    fun untouchedLegacyLoanRebuildsItsScheduleBeforePosting() = runBlocking {
        val client = ClientEntity(
            fullName = "Cliente legado nuevo",
            documentNumber = "LEGACY-NEW-001",
            phone = "0000000000",
            address = "Prueba"
        )
        database.clients().insert(client)
        val loan = LoanEntity(
            clientId = client.id,
            principalMinor = 10000L,
            balanceMinor = 10000L,
            currency = "DOP",
            periodicRate = "0.00",
            periods = 2,
            method = "FRANCES",
            frequency = "MONTHLY",
            firstDueDate = LocalDate.now().minusDays(1).toString()
        )
        database.loans().insert(loan)

        val result = database.payments().postPayment(
            PaymentEntity(
                operationId = "LEGACY-NEW-PAY-001",
                loanId = loan.id,
                amountMinor = 1000L,
                reference = "Primer pago tras reconstrucción"
            )
        )

        assertEquals(PaymentPostResult.POSTED, result)
        assertEquals(2, database.installments().findForLoan(loan.id).size)
        assertEquals(9000L, database.loans().findById(loan.id)?.balanceMinor)
    }

    @Test
    fun legacyLoanWithHistoricalPaymentsRequiresReconciliation() = runBlocking {
        val client = ClientEntity(
            fullName = "Cliente legado histórico",
            documentNumber = "LEGACY-HISTORY-001",
            phone = "0000000000",
            address = "Prueba"
        )
        database.clients().insert(client)
        val loan = LoanEntity(
            clientId = client.id,
            principalMinor = 10000L,
            balanceMinor = 9000L,
            currency = "DOP",
            periodicRate = "0.05",
            periods = 12,
            method = "FRANCES",
            firstDueDate = LocalDate.now().minusDays(1).toString()
        )
        database.loans().insert(loan)
        database.payments().insert(
            PaymentEntity(
                operationId = "LEGACY-HISTORY-OLD-001",
                loanId = loan.id,
                amountMinor = 1000L,
                reference = "Pago histórico sin distribución"
            )
        )

        val result = database.payments().postPayment(
            PaymentEntity(
                operationId = "LEGACY-HISTORY-NEW-001",
                loanId = loan.id,
                amountMinor = 500L,
                reference = "Nuevo intento"
            )
        )

        assertEquals(PaymentPostResult.LEGACY_RECONCILIATION_REQUIRED, result)
        assertEquals(0, database.installments().findForLoan(loan.id).size)
        assertEquals(9000L, database.loans().findById(loan.id)?.balanceMinor)
        assertEquals(null, database.payments().findByOperationId("LEGACY-HISTORY-NEW-001"))
    }

}
