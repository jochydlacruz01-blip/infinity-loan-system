package com.infinityloan.data

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomerCreditDaoTest {

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
    fun customerCreditIsSeparatedByCurrency() = runBlocking {

        val client = ClientEntity(
            fullName = "Cliente de crédito",
            documentNumber = "CREDIT-TEST-001",
            phone = "0000000000",
            address = "Prueba"
        )

        database.clients().insert(client)

        database.loans().insert(
            LoanEntity(
                id = "LOAN-001",
                clientId = client.id,
                principalMinor = 200000L,
                balanceMinor = 200000L,
                currency = "DOP",
                periodicRate = "0.05",
                periods = 10,
                method = "FRENCH"
            )
        )

        database.loans().insert(
            LoanEntity(
                id = "LOAN-002",
                clientId = client.id,
                principalMinor = 100000L,
                balanceMinor = 100000L,
                currency = "USD",
                periodicRate = "0.05",
                periods = 10,
                method = "FRENCH"
            )
        )

        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                operationId = "CREDIT-DOP-001",
                clientId = client.id,
                loanId = "LOAN-001",
                currency = "DOP",
                amountMinor = 100000L,
                reference = "Excedente DOP"
            )
        )

        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                operationId = "CREDIT-USD-001",
                clientId = client.id,
                loanId = "LOAN-002",
                currency = "USD",
                amountMinor = 50000L,
                reference = "Excedente USD"
            )
        )

        assertEquals(
            100000L,
            database.customerCredits().getBalance(client.id, "DOP")
        )

        assertEquals(
            50000L,
            database.customerCredits().getBalance(client.id, "USD")
        )
    }
        @Test
    fun customerCreditHistoryKeepsAllMovements() = runBlocking {

        val client = ClientEntity(
            fullName = "Cliente de historial",
            documentNumber = "CREDIT-TEST-002",
            phone = "0000000000",
            address = "Prueba"
        )

        database.clients().insert(client)

        database.loans().insert(
            LoanEntity(
                id = "LOAN-003",
                clientId = client.id,
                principalMinor = 200000L,
                balanceMinor = 200000L,
                currency = "DOP",
                periodicRate = "0.05",
                periods = 10,
                method = "FRENCH"
            )
        )

        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                operationId = "HISTORY-001",
                clientId = client.id,
                loanId = "LOAN-003",
                currency = "DOP",
                amountMinor = 100000L,
                reference = "Saldo generado"
            )
        )

        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                operationId = "HISTORY-002",
                clientId = client.id,
                loanId = "LOAN-003",
                currency = "DOP",
                amountMinor = -25000L,
                reference = "Saldo aplicado"
            )
        )

        val movements = database.customerCredits()
            .observeMovements(client.id, "DOP")
            .first()

        assertEquals(2, movements.size)

        assertEquals(
            75000L,
            database.customerCredits().getBalance(client.id, "DOP")
        )
    }
        @Test
    fun applyCreditDeductsAvailableBalance() = runBlocking {

        val client = ClientEntity(
            fullName = "Cliente aplicación",
            documentNumber = "CREDIT-APPLY-001",
            phone = "0000000000",
            address = "Prueba"
        )

        database.clients().insert(client)

        val loan = LoanEntity(
            id = "LOAN-APPLY-001",
            clientId = client.id,
            principalMinor = 100000L,
            balanceMinor = 100000L,
            currency = "DOP",
            periodicRate = "0.05",
            periods = 10,
            method = "FRENCH"
        )

        database.loans().insert(loan)

        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                operationId = "CREDIT-APPLY-001",
                clientId = client.id,
                loanId = loan.id,
                currency = "DOP",
                amountMinor = 100000L,
                reference = "Saldo a favor"
            )
        )

        val result = database.customerCredits().applyCredit(
            operationId = "CREDIT-USE-001",
            clientId = client.id,
            loanId = loan.id,
            currency = "DOP",
            amountMinor = 25000L,
            reference = "Aplicación de saldo"
        )

        assertEquals(
            CustomerCreditResult.APPLIED,
            result
        )

        assertEquals(
            75000L,
            database.customerCredits().getBalance(client.id, "DOP")
        )
    }
}
