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
}
