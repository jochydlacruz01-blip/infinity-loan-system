package com.infinityloan.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

        runBlocking {
            listOf("ADMIN-001", "ADMIN-002", "ADMIN-003", "ADMIN-004").forEachIndexed { index, id ->
                database.staffAccounts().insert(
                    StaffAccountEntity(
                        id = id,
                        username = "admin" + index,
                        displayName = "Administrador de prueba",
                        role = "ADMINISTRATOR",
                        passwordAlgorithm = "TEST",
                        passwordIterations = 1,
                        passwordSaltBase64 = "",
                        passwordHashBase64 = ""
                    )
                )
            }
            database.staffAccounts().insert(
                StaffAccountEntity(
                    id = "CASHIER-TEST",
                    username = "cashier",
                    displayName = "Cajero de prueba",
                    role = "CASHIER",
                    passwordAlgorithm = "TEST",
                    passwordIterations = 1,
                    passwordSaltBase64 = "",
                    passwordHashBase64 = ""
                )
            )
        }
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


    @Test
    fun cashPaymentReconcilesInstallmentAllocationCustomerCreditAndCashReceipt() = runBlocking {
        val loan = createDueLoan()
        val sessionId = "CASH-SESSION-RECONCILIATION"
        database.openCashSession(
            CashSessionEntity(
                sessionId = sessionId,
                staffId = "CASHIER-RECONCILIATION",
                currency = "DOP",
                openingMinor = 0L,
                openedAt = 1L
            )
        )
        val payment = PaymentEntity(
            operationId = "CASH-PAYMENT-RECONCILIATION",
            loanId = loan.id,
            amountMinor = 12500L,
            reference = "Pago superior al saldo vencido",
            createdAt = 2L
        )

        assertEquals(
            PaymentPostResult.POSTED,
            database.postCashLoanPayment(payment, sessionId)
        )

        val allocations = database.paymentAllocations().findForPayment(payment.operationId)
        val allocatedMinor = allocations.fold(0L) { total, row ->
            Math.addExact(
                total,
                Math.addExact(
                    Math.addExact(row.lateFeePaidMinor, row.chargesPaidMinor),
                    Math.addExact(row.interestPaidMinor, row.principalPaidMinor)
                )
            )
        }
        val creditMinor = database.customerCredits().getBalance(
            loan.clientId,
            loan.currency
        )
        val cashMovements = database.cashSessions().findMovements(sessionId)

        assertEquals(10000L, allocatedMinor)
        assertEquals(2500L, creditMinor)
        assertEquals(payment.amountMinor, Math.addExact(allocatedMinor, creditMinor))
        assertEquals(1, cashMovements.size)
        assertEquals(payment.amountMinor, cashMovements.single().amountMinor)
        assertEquals("CASH_IN", cashMovements.single().type)
        assertEquals(0L, database.loans().findById(loan.id)?.balanceMinor)
    }

    @Test
    fun cashPaymentCanBeReversedAtomicallyAndOnlyOnce() = runBlocking {
        val loan = createDueLoan()
        val sessionId = "CASH-SESSION-REVERSAL"
        database.openCashSession(CashSessionEntity(sessionId, "CASHIER-REVERSAL", "DOP", 5000L, 1L))
        val payment = PaymentEntity("CASH-PAYMENT-REVERSAL", loan.id, 3000L, "Pago reversible", 2L)
        assertEquals(PaymentPostResult.POSTED, database.postCashLoanPayment(payment, sessionId))
        assertEquals(PaymentReversalResult.REVERSED, database.reversePayment(payment.operationId, "ADMIN-001", "admin0", "Registro duplicado", 3L))
        assertEquals(PaymentReversalResult.ALREADY_REVERSED, database.reversePayment(payment.operationId, "ADMIN-001", "admin0", "Segundo intento", 4L))
        assertEquals(10000L, database.loans().findById(loan.id)?.balanceMinor)
        val movements = database.cashSessions().findMovements(sessionId)
        assertEquals(2, movements.size)
        assertEquals("CASH_IN", movements[0].type)
        assertEquals("CASH_OUT", movements[1].type)
        assertEquals(3000L, movements[1].amountMinor)
        assertEquals("PAYMENT_REVERSED", database.auditLogs().findByActor("ADMIN-001", 5).first().action)
    }

    @Test
    fun reversalRemovesUnusedGeneratedCreditAndRestoresInstallments() = runBlocking {
        val loan = createDueLoan()
        val payment = PaymentEntity("PAYMENT-EXCESS-REVERSAL", loan.id, 12500L, "Pago con excedente", 10L)
        assertEquals(PaymentPostResult.POSTED, database.payments().postPayment(payment))
        assertEquals(2500L, database.customerCredits().getBalance(loan.clientId, "DOP"))
        assertEquals(PaymentReversalResult.REVERSED, database.reversePayment(payment.operationId, "ADMIN-002", "admin1", "Cobro aplicado por error", 11L))
        assertEquals(0L, database.customerCredits().getBalance(loan.clientId, "DOP"))
        assertEquals(10000L, database.loans().findById(loan.id)?.balanceMinor)
        val installment = database.installments().findForLoan(loan.id).single()
        assertEquals(0L, installment.paidPrincipalMinor)
        assertEquals("PENDING", installment.status)
        assertEquals(12500L, database.paymentReversals().findByOriginalOperationId(payment.operationId)?.amountMinor)
    }

    @Test
    fun reversalIsBlockedWhenGeneratedCreditWasConsumed() = runBlocking {
        val loan = createDueLoan()
        val payment = PaymentEntity("PAYMENT-CREDIT-CONSUMED", loan.id, 12500L, "Pago con crédito generado", 20L)
        assertEquals(PaymentPostResult.POSTED, database.payments().postPayment(payment))
        database.customerCredits().insert(CustomerCreditMovementEntity(
            "CONSUME-GENERATED-CREDIT", loan.clientId, loan.id, loan.currency, -2500L,
            "Consumo posterior de crédito", 21L
        ))
        assertEquals(PaymentReversalResult.GENERATED_CREDIT_ALREADY_USED, database.reversePayment(payment.operationId, "ADMIN-003", "admin2", "Reverso bloqueado", 22L))
        assertEquals(null, database.paymentReversals().findByOriginalOperationId(payment.operationId))
        assertEquals(0L, database.loans().findById(loan.id)?.balanceMinor)
    }

    @Test
    fun reversalIsBlockedAfterOriginalCashSessionCloses() = runBlocking {
        val loan = createDueLoan()
        val sessionId = "CASH-SESSION-CLOSED-REVERSAL"
        database.openCashSession(CashSessionEntity(sessionId, "CASHIER-CLOSED", "DOP", 0L, 30L))
        val payment = PaymentEntity("CASH-PAYMENT-CLOSED-REVERSAL", loan.id, 1000L, "Cobro para cierre", 31L)
        assertEquals(PaymentPostResult.POSTED, database.postCashLoanPayment(payment, sessionId))
        database.closeCashSession(sessionId, countedMinor = 1000L, closedAt = 32L)
        assertEquals(PaymentReversalResult.ORIGINAL_CASH_SESSION_NOT_OPEN, database.reversePayment(payment.operationId, "ADMIN-004", "admin3", "Intento tras cierre", 33L))
        assertEquals(9000L, database.loans().findById(loan.id)?.balanceMinor)
        assertEquals(null, database.paymentReversals().findByOriginalOperationId(payment.operationId))
    }


    @Test
    fun cashierCannotReverseEvenWhenCallingDatabaseDirectly() = runBlocking {
        val loan = createDueLoan()
        val payment = PaymentEntity("PAYMENT-UNAUTHORIZED-REVERSAL", loan.id, 1000L, "Pago protegido", 40L)
        assertEquals(PaymentPostResult.POSTED, database.payments().postPayment(payment))
        assertEquals(
            PaymentReversalResult.INVALID_ACTOR,
            database.reversePayment(payment.operationId, "CASHIER-TEST", "cashier", "Motivo no autorizado", 41L)
        )
        assertEquals(9000L, database.loans().findById(loan.id)?.balanceMinor)
        assertEquals(null, database.paymentReversals().findByOriginalOperationId(payment.operationId))
    }

    @Test
    fun reversalServiceRejectsCashierBeforeDatabaseOperation() = runBlocking {
        val service = PaymentReversalService(
            database,
            com.infinityloan.security.StaffAuthenticationService(database.staffAccounts())
        )
        val session = com.infinityloan.security.StaffSession(
            staffId = "CASHIER-TEST",
            username = "cashier",
            displayName = "Cajero de prueba",
            profile = com.infinityloan.security.AccessProfile(com.infinityloan.security.StaffRole.CASHIER)
        )
        assertEquals(
            PaymentReversalResult.INVALID_ACTOR,
            service.reverse(session, "NONEXISTENT-PAYMENT", "Intento sin permiso")
        )
    }


    @Test
    fun reversalRestoresCustomerCreditUsedToFundPayment() = runBlocking {
        val loan = createDueLoan()
        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                "SEED-CREDIT-REVERSAL", loan.clientId, loan.id, loan.currency, 10000L,
                "Crédito inicial del cliente", 1L
            )
        )
        val payment = PaymentEntity("PAYMENT-FUNDED-BY-CREDIT", loan.id, 10000L, "Pago con crédito", 50L)
        assertEquals(CreditPaymentResult.POSTED, database.postPaymentWithCustomerCredit(payment))
        assertEquals(0L, database.customerCredits().getBalance(loan.clientId, loan.currency))
        assertEquals(
            PaymentReversalResult.REVERSED,
            database.reversePayment(payment.operationId, "ADMIN-001", "admin0", "Aplicación de crédito incorrecta", 51L)
        )
        assertEquals(10000L, database.customerCredits().getBalance(loan.clientId, loan.currency))
        assertEquals(10000L, database.loans().findById(loan.id)?.balanceMinor)
        assertEquals("PENDING", database.installments().findForLoan(loan.id).single().status)
    }

    @Test
    fun reversalIsBlockedWhenAnotherPaymentExistsForTheLoan() = runBlocking {
        val loan = createDueLoan()
        val first = PaymentEntity("PAYMENT-FIRST-REVERSAL", loan.id, 1000L, "Primer pago", 60L)
        val second = PaymentEntity("PAYMENT-SECOND-REVERSAL", loan.id, 1000L, "Segundo pago", 61L)
        assertEquals(PaymentPostResult.POSTED, database.payments().postPayment(first))
        assertEquals(PaymentPostResult.POSTED, database.payments().postPayment(second))
        assertEquals(
            PaymentReversalResult.LATER_PAYMENT_EXISTS,
            database.reversePayment(first.operationId, "ADMIN-001", "admin0", "Reverso fuera de secuencia", 62L)
        )
        assertEquals(8000L, database.loans().findById(loan.id)?.balanceMinor)
        assertEquals(null, database.paymentReversals().findByOriginalOperationId(first.operationId))
    }

    @Test
    fun reversalLedgerConflictRollsBackEveryAccountingChange() = runBlocking {
        val loan = createDueLoan()
        val payment = PaymentEntity("PAYMENT-REVERSAL-ROLLBACK", loan.id, 12500L, "Pago para rollback", 70L)
        assertEquals(PaymentPostResult.POSTED, database.payments().postPayment(payment))
        database.customerCredits().insert(
            CustomerCreditMovementEntity(
                "PAYMENT-REVERSAL-CREDIT-" + payment.operationId,
                loan.clientId, loan.id, loan.currency, 1L,
                "Colisión intencional de idempotencia", 71L
            )
        )
        var rolledBack = false
        try {
            database.reversePayment(payment.operationId, "ADMIN-001", "admin0", "Prueba de rollback", 72L)
        } catch (_: IllegalStateException) {
            rolledBack = true
        }
        assertEquals(true, rolledBack)
        assertEquals(0L, database.loans().findById(loan.id)?.balanceMinor)
        assertEquals(2501L, database.customerCredits().getBalance(loan.clientId, loan.currency))
        assertEquals(null, database.paymentReversals().findByOriginalOperationId(payment.operationId))
        assertEquals(0, database.auditLogs().findByActor("ADMIN-001", 5).size)
    }



    @Test
    fun persistedCashReconciliationReadsReceiptsAndFindsOrphanMovements() = runBlocking {
        val loan = createDueLoan()
        val sessionId = "CASH-SESSION-PERSISTED-AUDIT"
        database.openCashSession(
            CashSessionEntity(
                sessionId = sessionId,
                staffId = "CASHIER-PERSISTED-AUDIT",
                currency = "DOP",
                openingMinor = 0L,
                openedAt = 1L
            )
        )
        val payment = PaymentEntity(
            operationId = "CASH-PAY-PERSISTED-AUDIT",
            loanId = loan.id,
            amountMinor = 2500L,
            reference = "Pago para conciliación persistida",
            createdAt = 2L
        )
        assertEquals(PaymentPostResult.POSTED, database.postCashLoanPayment(payment, sessionId))

        val clean = database.reconcilePersistedCashPayments()
        assertEquals(1, clean.checkedPayments)
        assertEquals(emptyList<com.infinityloan.finance.CashReconciliationIssue>(), clean.findings)

        database.cashSessions().insertMovement(
            CashMovementEntity(
                operationId = "CASH-PAY-MISSING-PAYMENT",
                sessionId = sessionId,
                type = "CASH_IN",
                amountMinor = 700L,
                reason = "Recibo sin pago asociado para prueba de auditoría",
                createdAt = 3L
            )
        )

        val withOrphan = database.reconcilePersistedCashPayments()
        assertEquals(1, withOrphan.checkedPayments)
        assertTrue(
            withOrphan.findings.any {
                it.type == com.infinityloan.finance.CashReconciliationIssueType.ORPHAN_CASH_MOVEMENT
            }
        )
    }

    @Test
    fun persistedCashClosingAuditRebuildsExpectedCashPerEmployeeAndCurrency() = runBlocking {
        val closedId = "CASH-SESSION-CLOSE-AUDIT-REPORT"
        val openId = "CASH-SESSION-OPEN-AUDIT-REPORT"
        database.openCashSession(
            CashSessionEntity(closedId, "CASHIER-AUDIT-REPORT", "DOP", 5000L, 10L)
        )
        database.postCashMovement(
            CashMovementEntity(
                "CASH-AUDIT-REPORT-IN", closedId, "CASH_IN", 1500L,
                "Ingreso de prueba", 11L
            )
        )
        database.closeCashSession(closedId, countedMinor = 6200L, closedAt = 12L)

        database.openCashSession(
            CashSessionEntity(openId, "CASHIER-AUDIT-REPORT-2", "USD", 900L, 13L)
        )

        val report = database.auditPersistedCashClosings()
        assertEquals(2, report.sessionsChecked)
        val closed = report.rows.first { it.sessionId == closedId }
        assertEquals("CASHIER-AUDIT-REPORT", closed.staffId)
        assertEquals("DOP", closed.currency)
        assertEquals(true, closed.closed)
        assertEquals(6500L, closed.expectedCashMinor)
        assertEquals(6200L, closed.countedCashMinor)
        assertEquals(-300L, closed.varianceMinor)
        assertNull(closed.issue)

        val open = report.rows.first { it.sessionId == openId }
        assertEquals(false, open.closed)
        assertEquals(900L, open.expectedCashMinor)
        assertNull(open.countedCashMinor)
        assertNull(open.varianceMinor)
        assertNull(open.issue)
    }

    @Test
    fun persistedStaffCashAuditGroupsByEmployeeAndCurrencyAndKeepsOpenAndClosedTotalsSeparate() = runBlocking {
        val closedDop = "STAFF-AUDIT-CLOSED-DOP"
        val openUsd = "STAFF-AUDIT-OPEN-USD"
        val otherStaffDop = "STAFF-AUDIT-OTHER-DOP"

        database.openCashSession(CashSessionEntity(closedDop, "STAFF-A", "DOP", 5000L, 100L))
        database.postCashMovement(
            CashMovementEntity("STAFF-AUDIT-IN", closedDop, "CASH_IN", 1500L, "Ingreso", 101L)
        )
        database.closeCashSession(closedDop, countedMinor = 6400L, closedAt = 102L)

        database.openCashSession(CashSessionEntity(openUsd, "STAFF-A", "USD", 900L, 103L))
        database.openCashSession(CashSessionEntity(otherStaffDop, "STAFF-B", "DOP", 2500L, 104L))

        val report = database.auditPersistedCashByStaff()

        assertEquals(0, report.invalidSessionCount)
        assertTrue(report.invalidSessionIds.isEmpty())
        assertEquals(3, report.staffRows.size)

        val staffADop = report.staffRows.first { it.staffId == "STAFF-A" && it.currency == "DOP" }
        assertEquals(1, staffADop.sessionCount)
        assertEquals(0, staffADop.openSessionCount)
        assertEquals(1, staffADop.closedSessionCount)
        assertEquals(6500L, staffADop.expectedCashMinor)
        assertEquals(6400L, staffADop.countedCashMinor)
        assertEquals(-100L, staffADop.varianceMinor)

        val staffAUsd = report.staffRows.first { it.staffId == "STAFF-A" && it.currency == "USD" }
        assertEquals(1, staffAUsd.openSessionCount)
        assertEquals(0, staffAUsd.closedSessionCount)
        assertEquals(900L, staffAUsd.expectedCashMinor)
        assertEquals(0L, staffAUsd.countedCashMinor)
        assertEquals(0L, staffAUsd.varianceMinor)

        val staffBDop = report.staffRows.first { it.staffId == "STAFF-B" && it.currency == "DOP" }
        assertEquals(2500L, staffBDop.expectedCashMinor)
        assertEquals(1, staffBDop.openSessionCount)
    }


    @Test
    fun persistedCashAuditQuarantinesUnknownMovementTypesWithoutBreakingValidSessions() = runBlocking {
        val invalidSessionId = "CASH-AUDIT-INVALID-TYPE"
        val validSessionId = "CASH-AUDIT-VALID-SESSION"

        database.openCashSession(
            CashSessionEntity(invalidSessionId, "STAFF-INVALID", "DOP", 1000L, 200L)
        )
        // Simula una fila histórica corrupta que pudo quedar de una versión anterior.
        database.cashSessions().insertMovement(
            CashMovementEntity(
                operationId = "CASH-AUDIT-UNKNOWN-MOVEMENT",
                sessionId = invalidSessionId,
                type = "UNKNOWN_MOVEMENT_TYPE",
                amountMinor = 100L,
                reason = "Registro legado inválido",
                createdAt = 201L
            )
        )

        database.openCashSession(
            CashSessionEntity(validSessionId, "STAFF-VALID", "DOP", 2500L, 202L)
        )
        database.postCashMovement(
            CashMovementEntity(
                operationId = "CASH-AUDIT-VALID-IN",
                sessionId = validSessionId,
                type = "CASH_IN",
                amountMinor = 500L,
                reason = "Ingreso válido",
                createdAt = 203L
            )
        )

        val closingAudit = database.auditPersistedCashClosings()
        val invalid = closingAudit.rows.single { it.sessionId == invalidSessionId }
        assertTrue(invalid.issue?.isNotBlank() == true)
        assertNull(invalid.expectedCashMinor)

        val staffAudit = database.auditPersistedCashByStaff()
        assertEquals(1, staffAudit.invalidSessionCount)
        assertTrue(invalidSessionId in staffAudit.invalidSessionIds)
        assertTrue(staffAudit.staffRows.none { it.staffId == "STAFF-INVALID" })

        val validStaffRow = staffAudit.staffRows.single {
            it.staffId == "STAFF-VALID" && it.currency == "DOP"
        }
        assertEquals(3000L, validStaffRow.expectedCashMinor)
        assertEquals(0L, validStaffRow.varianceMinor)
    }


    @Test
    fun cashVarianceReviewRequiresIndependentAuthorizedReviewerAndPersistsImmutableSnapshot() = runBlocking {
        val sessionId = "CASH-SESSION-VARIANCE-REVIEW"
        database.openCashSession(
            CashSessionEntity(
                sessionId = sessionId,
                staffId = "CASHIER-TEST",
                currency = "DOP",
                openingMinor = 5000L,
                openedAt = 100L
            )
        )
        database.postCashMovement(
            CashMovementEntity(
                operationId = "VARIANCE-REVIEW-IN",
                sessionId = sessionId,
                type = "CASH_IN",
                amountMinor = 1500L,
                reason = "Cobro registrado",
                createdAt = 101L
            )
        )
        database.closeCashSession(sessionId, countedMinor = 6300L, closedAt = 102L)

        val authentication = com.infinityloan.security.StaffAuthenticationService(database.staffAccounts())
        val service = CashVarianceReviewService(database, authentication) { 200L }
        val administrator = com.infinityloan.security.StaffSession(
            staffId = "ADMIN-001",
            username = "admin0",
            displayName = "Administrador de prueba",
            profile = com.infinityloan.security.AccessProfile(
                com.infinityloan.security.StaffRole.ADMINISTRATOR
            )
        )

        val review = service.review(
            administrator,
            sessionId,
            CashVarianceDecision.APPROVED,
            "Conteo físico verificado y diferencia justificada."
        )

        assertEquals(CashVarianceDecision.APPROVED.name, review.decision)
        assertEquals("DOP", review.currency)
        assertEquals(6500L, review.expectedCashMinor)
        assertEquals(6300L, review.countedCashMinor)
        assertEquals(-200L, review.varianceMinor)
        assertEquals(
            review,
            database.cashVarianceReviews().findForSession(sessionId).single()
        )
        val audit = database.auditLogs().findByActor("ADMIN-001", 10)
            .single { it.action == "CASH_VARIANCE_REVIEWED" }
        assertEquals(sessionId, audit.targetId)
        assertTrue(audit.details.contains("reviewId=${review.reviewId}"))

        // A second decision is appended; the first review is never overwritten.
        val followUp = service.review(
            administrator,
            sessionId,
            CashVarianceDecision.REQUIRES_INVESTIGATION,
            "Se solicita una segunda comprobación independiente."
        )
        assertEquals(2, database.cashVarianceReviews().findForSession(sessionId).size)
        assertEquals(CashVarianceDecision.APPROVED.name, review.decision)
        assertEquals(CashVarianceDecision.REQUIRES_INVESTIGATION.name, followUp.decision)
    }

    @Test
    fun cashVarianceReviewRejectsCashierAndSessionOwnerWithoutWritingRecords() = runBlocking {
        val sessionId = "CASH-SESSION-VARIANCE-DENIED"
        database.openCashSession(
            CashSessionEntity(
                sessionId = sessionId,
                staffId = "CASHIER-TEST",
                currency = "DOP",
                openingMinor = 1000L,
                openedAt = 300L
            )
        )
        database.closeCashSession(sessionId, countedMinor = 900L, closedAt = 301L)

        val authentication = com.infinityloan.security.StaffAuthenticationService(database.staffAccounts())
        val service = CashVarianceReviewService(database, authentication)
        val cashier = com.infinityloan.security.StaffSession(
            staffId = "CASHIER-TEST",
            username = "cashier",
            displayName = "Cajero de prueba",
            profile = com.infinityloan.security.AccessProfile(
                com.infinityloan.security.StaffRole.CASHIER
            )
        )
        val administrator = com.infinityloan.security.StaffSession(
            staffId = "ADMIN-001",
            username = "admin0",
            displayName = "Administrador de prueba",
            profile = com.infinityloan.security.AccessProfile(
                com.infinityloan.security.StaffRole.ADMINISTRATOR
            )
        )

        var cashierRejected = false
        try {
            service.review(cashier, sessionId, CashVarianceDecision.APPROVED, "Intento sin permiso")
        } catch (_: IllegalArgumentException) {
            cashierRejected = true
        }
        assertTrue(cashierRejected)

        var ownerRejected = false
        try {
            service.review(
                cashier.copy(
                    staffId = "CASHIER-TEST",
                    username = "cashier",
                    profile = com.infinityloan.security.AccessProfile(
                        com.infinityloan.security.StaffRole.ADMINISTRATOR
                    )
                ),
                sessionId,
                CashVarianceDecision.APPROVED,
                "Intento de auto revisión"
            )
        } catch (_: IllegalArgumentException) {
            ownerRejected = true
        }
        assertTrue(ownerRejected)
        assertTrue(database.cashVarianceReviews().findForSession(sessionId).isEmpty())
        assertTrue(database.auditLogs().findByActor("ADMIN-001", 10).isEmpty())
    }

}
