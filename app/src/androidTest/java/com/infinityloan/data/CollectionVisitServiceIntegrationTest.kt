package com.infinityloan.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.infinityloan.security.AccessProfile
import com.infinityloan.security.StaffRole
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CollectionVisitServiceIntegrationTest {
    private lateinit var database: InfinityDatabase
    private lateinit var service: CollectionVisitService
    private lateinit var actor: com.infinityloan.security.StaffSession

    @Before
    fun setup() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, InfinityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val staff = StaffAccountEntity(
            id = "VISIT-INTEGRATION-ADMIN",
            username = "visit-admin",
            displayName = "Administrador de integración",
            role = StaffRole.ADMINISTRATOR.name,
            passwordAlgorithm = "TEST",
            passwordIterations = 1,
            passwordSaltBase64 = "",
            passwordHashBase64 = ""
        )
        database.staffAccounts().insert(staff)
        actor = com.infinityloan.security.StaffSession(
            staffId = staff.id,
            username = staff.username,
            displayName = staff.displayName,
            profile = AccessProfile(role = StaffRole.ADMINISTRATOR)
        )
        val authentication = com.infinityloan.security.StaffAuthenticationService(
            database.staffAccounts(),
            audit = { database.auditLogs().insert(it) },
            persistStaffChange = { operation -> database.withTransaction { operation() } }
        )
        service = CollectionVisitService(database, authentication)
    }

    @After
    fun cleanup() {
        database.close()
    }

    private suspend fun createLoan(id: String, document: String): LoanEntity {
        val client = ClientEntity(
            fullName = "Cliente $id",
            documentNumber = document,
            phone = "0000000000",
            address = "Prueba"
        )
        database.clients().insert(client)
        val loan = LoanEntity(
            id = id,
            clientId = client.id,
            principalMinor = 10000L,
            balanceMinor = 10000L,
            currency = "DOP",
            periodicRate = "0",
            periods = 1,
            method = "FRENCH",
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
                    remainingPrincipalMinor = 10000L
                )
            )
        )
        return loan
    }

    private suspend fun createVisit(loan: LoanEntity, id: String) {
        database.collectionVisits().insert(
            CollectionVisitEntity(
                visitId = id,
                loanId = loan.id,
                clientId = loan.clientId,
                collectorId = actor.staffId,
                plannedDate = LocalDate.now().toString()
            )
        )
    }

    private suspend fun postPayment(loan: LoanEntity, operationId: String) {
        assertEquals(
            PaymentPostResult.POSTED,
            database.payments().postPayment(
                PaymentEntity(
                    operationId = operationId,
                    loanId = loan.id,
                    amountMinor = 1000L,
                    reference = "Recibo de integración"
                )
            )
        )
    }

    @Test
    fun verifiedPaymentClosesVisitAndWritesAuditAtomically() = runBlocking {
        val loan = createLoan("VISIT-LOAN-VALID", "VISIT-DOC-VALID")
        createVisit(loan, "VISIT-VALID")
        postPayment(loan, "VISIT-PAYMENT-VALID")

        assertTrue(service.markPaid(actor, "VISIT-VALID", "VISIT-PAYMENT-VALID", "Cobro verificado"))

        assertEquals("PAID", database.collectionVisits().findById("VISIT-VALID")?.status)

        // The visit is evidence of a real posted payment, not a substitute for accounting.
        assertEquals(9000L, database.loans().findById(loan.id)?.balanceMinor)
        val installment = database.installments().findForLoan(loan.id).single()
        assertEquals(1000L, installment.paidPrincipalMinor)
        assertEquals(9000L, installment.remainingPrincipalMinor)
        val allocation = database.paymentAllocations()
            .findForPayment("VISIT-PAYMENT-VALID").single()
        assertEquals(1000L, allocation.principalPaidMinor)
        assertEquals(
            "VISIT-PAYMENT-VALID",
            database.payments().findByOperationId("VISIT-PAYMENT-VALID")?.operationId
        )

        // A repeated completion must not create a second paid transition or audit event.
        val duplicateRejected = runCatching {
            service.markPaid(actor, "VISIT-VALID", "VISIT-PAYMENT-VALID", "Segundo intento")
        }.isFailure
        assertTrue(duplicateRejected)
        assertEquals(
            1,
            database.auditLogs().observeRecent(100).first().count {
                it.action == "COLLECTION_VISIT_MARKED_PAID" && it.targetId == "VISIT-VALID"
            }
        )
    }

    @Test
    fun paymentFromAnotherLoanCannotCloseVisit() = runBlocking {
        val visitLoan = createLoan("VISIT-LOAN-OWNER", "VISIT-DOC-OWNER")
        val otherLoan = createLoan("VISIT-LOAN-OTHER", "VISIT-DOC-OTHER")
        createVisit(visitLoan, "VISIT-WRONG-LOAN")
        postPayment(otherLoan, "VISIT-PAYMENT-WRONG-LOAN")

        val rejected = runCatching {
            service.markPaid(actor, "VISIT-WRONG-LOAN", "VISIT-PAYMENT-WRONG-LOAN")
        }.isFailure

        assertTrue(rejected)
        assertEquals("SCHEDULED", database.collectionVisits().findById("VISIT-WRONG-LOAN")?.status)
        assertEquals(
            0,
            database.auditLogs().observeRecent(100).first().count {
                it.action == "COLLECTION_VISIT_MARKED_PAID" && it.targetId == "VISIT-WRONG-LOAN"
            }
        )
    }

    @Test
    fun reversedPaymentCannotCloseVisit() = runBlocking {
        val loan = createLoan("VISIT-LOAN-REVERSED", "VISIT-DOC-REVERSED")
        createVisit(loan, "VISIT-REVERSED")
        postPayment(loan, "VISIT-PAYMENT-REVERSED")

        assertEquals(
            PaymentReversalResult.REVERSED,
            database.reversePayment(
                operationId = "VISIT-PAYMENT-REVERSED",
                actorStaffId = actor.staffId,
                actorUsername = actor.username,
                reason = "Reverso de prueba"
            )
        )

        val rejected = runCatching {
            service.markPaid(actor, "VISIT-REVERSED", "VISIT-PAYMENT-REVERSED")
        }.isFailure

        assertTrue(rejected)
        assertEquals("SCHEDULED", database.collectionVisits().findById("VISIT-REVERSED")?.status)
        assertEquals(
            0,
            database.auditLogs().observeRecent(100).first().count {
                it.action == "COLLECTION_VISIT_MARKED_PAID" && it.targetId == "VISIT-REVERSED"
            }
        )
    }

    @Test
    fun cancelledVisitCannotBeCompletedByPayment() = runBlocking {
        val loan = createLoan("VISIT-LOAN-CANCELLED", "VISIT-DOC-CANCELLED")
        createVisit(loan, "VISIT-CANCELLED")
        postPayment(loan, "VISIT-PAYMENT-CANCELLED")
        assertEquals(
            1,
            database.collectionVisits().transition(
                visitId = "VISIT-CANCELLED",
                expectedStatus = "SCHEDULED",
                status = "CANCELLED",
                note = "Cancelada",
                updatedAt = System.currentTimeMillis()
            )
        )

        val rejected = runCatching {
            service.markPaid(actor, "VISIT-CANCELLED", "VISIT-PAYMENT-CANCELLED")
        }.isFailure

        assertTrue(rejected)
        assertFalse(database.collectionVisits().findById("VISIT-CANCELLED")?.status == "PAID")
    }

    @Test
    fun disabledStaffSessionCannotCloseVisit() = runBlocking {
        val loan = createLoan("VISIT-LOAN-DISABLED", "VISIT-DOC-DISABLED")
        createVisit(loan, "VISIT-DISABLED-STAFF")
        postPayment(loan, "VISIT-PAYMENT-DISABLED-STAFF")
        database.staffAccounts().setActive(actor.staffId, false)

        val rejected = runCatching {
            service.markPaid(actor, "VISIT-DISABLED-STAFF", "VISIT-PAYMENT-DISABLED-STAFF")
        }.isFailure

        assertTrue(rejected)
        assertEquals("SCHEDULED", database.collectionVisits().findById("VISIT-DISABLED-STAFF")?.status)
        assertEquals(
            0,
            database.auditLogs().observeRecent(100).first().count {
                it.action == "COLLECTION_VISIT_MARKED_PAID" && it.targetId == "VISIT-DISABLED-STAFF"
            }
        )
    }

    @Test
    fun visitClientMismatchCannotBeClosedByValidLoanPayment() = runBlocking {
        val loan = createLoan("VISIT-LOAN-CLIENT-MISMATCH", "VISIT-DOC-LOAN-OWNER")
        val unrelatedLoan = createLoan("VISIT-LOAN-OTHER-CLIENT", "VISIT-DOC-OTHER-CLIENT")
        database.collectionVisits().insert(
            CollectionVisitEntity(
                visitId = "VISIT-CLIENT-MISMATCH",
                loanId = loan.id,
                clientId = unrelatedLoan.clientId,
                collectorId = actor.staffId,
                plannedDate = LocalDate.now().toString()
            )
        )
        postPayment(loan, "VISIT-PAYMENT-CLIENT-MISMATCH")

        val rejected = runCatching {
            service.markPaid(actor, "VISIT-CLIENT-MISMATCH", "VISIT-PAYMENT-CLIENT-MISMATCH")
        }.isFailure

        assertTrue(rejected)
        assertEquals(
            "SCHEDULED",
            database.collectionVisits().findById("VISIT-CLIENT-MISMATCH")?.status
        )
        assertEquals(
            0,
            database.auditLogs().observeRecent(100).first().count {
                it.action == "COLLECTION_VISIT_MARKED_PAID" && it.targetId == "VISIT-CLIENT-MISMATCH"
            }
        )
    }

}
