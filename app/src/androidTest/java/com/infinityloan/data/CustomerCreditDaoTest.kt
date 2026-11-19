package com.infinityloan.data
 
import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
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

    @Test
    fun applyCreditRejectsBlankReferenceAndUnsupportedCurrencyAndOperationId() = runBlocking {
        val client = ClientEntity(
            fullName = "Cliente validación",
            documentNumber = "CREDIT-VALIDATION-001",
            phone = "0000000000",
            address = "Prueba"
        )
        database.clients().insert(client)
        val loan = LoanEntity(
            id = "LOAN-VALIDATION-001",
            clientId = client.id,
            principalMinor = 10000L,
            balanceMinor = 10000L,
            currency = "DOP",
            periodicRate = "0",
            periods = 1,
            method = "FRENCH"
        )
        database.loans().insert(loan)
        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                operationId = "VALIDATION-CREDIT-001",
                clientId = client.id,
                loanId = loan.id,
                currency = "DOP",
                amountMinor = 1000L,
                reference = "Saldo disponible"
            )
        )

        assertEquals(CustomerCreditResult.INVALID_REFERENCE,
            database.customerCredits().applyCredit("VALID-1", client.id, loan.id, "DOP", 100L, "  "))
        assertEquals(CustomerCreditResult.INVALID_CURRENCY,
            database.customerCredits().applyCredit("VALID-2", client.id, loan.id, "MXN", 100L, "Referencia"))
        assertEquals(CustomerCreditResult.INVALID_OPERATION_ID,
            database.customerCredits().applyCredit(" ", client.id, loan.id, "DOP", 100L, "Referencia"))
        assertEquals(1000L, database.customerCredits().getBalance(client.id, "DOP"))
    }

    @Test(expected = ArithmeticException::class)
    fun customerCreditBalanceRejectsLongOverflow() = runBlocking {
        val client = ClientEntity(
            fullName = "Cliente saldo límite",
            documentNumber = "CREDIT-OVERFLOW-001",
            phone = "0000000000",
            address = "Prueba"
        )
        database.clients().insert(client)
        database.loans().insert(
            LoanEntity(
                id = "LOAN-OVERFLOW-001",
                clientId = client.id,
                principalMinor = 100L,
                balanceMinor = 100L,
                currency = "DOP",
                periodicRate = "0",
                periods = 1,
                method = "FRENCH"
            )
        )
        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                operationId = "CREDIT-OVERFLOW-001",
                clientId = client.id,
                loanId = "LOAN-OVERFLOW-001",
                currency = "DOP",
                amountMinor = Long.MAX_VALUE,
                reference = "Primer movimiento"
            )
        )
        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                operationId = "CREDIT-OVERFLOW-002",
                clientId = client.id,
                loanId = "LOAN-OVERFLOW-001",
                currency = "DOP",
                amountMinor = 1L,
                reference = "Segundo movimiento"
            )
        )

        database.customerCredits().getBalance(client.id, "DOP")
        Unit
    }

    @Test
    fun concurrentCreditApplicationsCannotOverdrawCustomerBalance() = runBlocking {
        val client = ClientEntity(
            fullName = "Cliente crédito concurrente",
            documentNumber = "CREDIT-CONCURRENT-001",
            phone = "0000000000",
            address = "Prueba"
        )
        database.clients().insert(client)
        val loan = LoanEntity(
            id = "LOAN-CREDIT-CONCURRENT-001",
            clientId = client.id,
            principalMinor = 200000L,
            balanceMinor = 200000L,
            currency = "DOP",
            periodicRate = "0.05",
            periods = 10,
            method = "FRENCH"
        )
        database.loans().insert(loan)
        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                operationId = "CREDIT-CONCURRENT-INITIAL",
                clientId = client.id,
                loanId = loan.id,
                currency = "DOP",
                amountMinor = 100000L,
                reference = "Crédito disponible"
            )
        )

        val results = listOf(
            async(Dispatchers.IO) {
                database.customerCredits().applyCredit(
                    "CREDIT-CONCURRENT-USE-1", client.id, loan.id, "DOP",
                    70000L, "Aplicación concurrente 1"
                )
            },
            async(Dispatchers.IO) {
                database.customerCredits().applyCredit(
                    "CREDIT-CONCURRENT-USE-2", client.id, loan.id, "DOP",
                    70000L, "Aplicación concurrente 2"
                )
            }
        ).awaitAll()

        assertEquals(1, results.count { it == CustomerCreditResult.APPLIED })
        assertEquals(1, results.count { it == CustomerCreditResult.INSUFFICIENT_CREDIT })
        assertEquals(30000L, database.customerCredits().getBalance(client.id, "DOP"))
    }
}
