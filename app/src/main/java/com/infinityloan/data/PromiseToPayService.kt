package com.infinityloan.data

import androidx.room.withTransaction
import com.infinityloan.finance.PromiseToPayRecord
import com.infinityloan.finance.PromiseToPayStatus
import com.infinityloan.finance.PromiseToPayWorkflow
import com.infinityloan.security.StaffRole
import com.infinityloan.security.Permission
import com.infinityloan.security.StaffAuthenticationService
import com.infinityloan.security.StaffSession
import java.time.LocalDate

/**
 * Persistence boundary for promises. Every mutation revalidates the live session,
 * enforces capability checks, and commits the business row with its audit event.
 */
class PromiseToPayService(
    private val database: InfinityDatabase,
    private val authentication: StaffAuthenticationService
) {
    suspend fun create(
        actor: StaffSession,
        loanId: String,
        promisedAmountMinor: Long,
        promisedDate: LocalDate,
        note: String = ""
    ): PromiseToPayEntity {
        require(authentication.isSessionValid(actor)) { "La sesión ha caducado." }
        require(authentication.hasPermission(actor, Permission.PROMISE_TO_PAY_CREATE)) {
            "No tienes permiso para registrar promesas de pago."
        }
        val promise = database.withTransaction {
            require(authentication.isSessionValid(actor)) { "La sesión ha caducado." }
            require(authentication.hasPermission(actor, Permission.PROMISE_TO_PAY_CREATE)) {
                "No tienes permiso para registrar promesas de pago."
            }
            val loan = database.loans().findById(loanId)
                ?: throw IllegalArgumentException("El préstamo no existe.")
            require(loan.status == "ACTIVE") { "El préstamo no está activo." }
            require(loan.balanceMinor > 0L) { "El préstamo no tiene saldo pendiente." }
            require(promisedAmountMinor in 1L..loan.balanceMinor) {
                "El importe prometido debe ser positivo y no superar el saldo pendiente del préstamo."
            }
            val record = PromiseToPayRecord(
                loanId = loan.id,
                clientId = loan.clientId,
                collectorId = actor.staffId,
                promisedAmountMinor = promisedAmountMinor,
                currency = loan.currency,
                promisedDate = promisedDate,
                note = note.trim()
            )
            PromiseToPayWorkflow.validate(record)
            val entity = PromiseToPayEntity(
                promiseId = record.promiseId,
                loanId = record.loanId,
                clientId = record.clientId,
                collectorId = record.collectorId,
                promisedAmountMinor = record.promisedAmountMinor,
                currency = record.currency,
                promisedDate = record.promisedDate.toString(),
                status = record.status.name,
                note = record.note
            )
            database.promisesToPay().insert(entity)
            database.auditLogs().insert(AuditLogEntity(
                actorStaffId = actor.staffId,
                actorUsername = actor.username,
                action = "PROMISE_TO_PAY_CREATED",
                targetType = "PROMISE_TO_PAY",
                targetId = entity.promiseId,
                details = "loanId=${entity.loanId};amountMinor=${entity.promisedAmountMinor};currency=${entity.currency};promisedDate=${entity.promisedDate}"
            ))
            entity
        }
        return promise
    }

    /** Links a posted ledger payment as explicit promise fulfillment evidence. */
    suspend fun linkPayment(actor: StaffSession, promiseId: String, operationId: String, amountMinor: Long): PromisePaymentLinkEntity {
        require(authentication.isSessionValid(actor)) { "La sesión ha caducado." }
        require(authentication.hasPermission(actor, Permission.PROMISE_TO_PAY_RESOLVE)) { "No tienes permiso para conciliar promesas de pago." }
        require(amountMinor > 0L) { "El importe vinculado debe ser mayor que cero." }
        return database.withTransaction {
            require(authentication.isSessionValid(actor)) { "La sesión ha caducado." }
            require(authentication.hasPermission(actor, Permission.PROMISE_TO_PAY_RESOLVE)) {
                "No tienes permiso para conciliar promesas de pago."
            }
            val promise = database.promisesToPay().findById(promiseId) ?: throw IllegalArgumentException("La promesa no existe.")
            require(promise.status == PromiseToPayStatus.OPEN.name) { "Solo se pueden conciliar promesas abiertas." }
            PromiseToPayWorkflow.validate(
                PromiseToPayRecord(
                    promiseId = promise.promiseId,
                    loanId = promise.loanId,
                    clientId = promise.clientId,
                    collectorId = promise.collectorId,
                    promisedAmountMinor = promise.promisedAmountMinor,
                    currency = promise.currency,
                    promisedDate = LocalDate.parse(promise.promisedDate),
                    status = PromiseToPayStatus.valueOf(promise.status),
                    note = promise.note
                )
            )
            if (actor.profile.role == StaffRole.COLLECTOR || actor.profile.role == StaffRole.CASHIER) {
                require(promise.collectorId == actor.staffId) { "Solo puedes conciliar las promesas que registraste." }
            }
            val payment = database.payments().findByOperationId(operationId) ?: throw IllegalArgumentException("El pago no existe en el libro contable.")
            require(payment.amountMinor > 0L) { "El pago tiene un importe inválido." }
            require(payment.loanId == promise.loanId) { "El pago pertenece a otro préstamo." }
            require(payment.createdAt >= promise.createdAt) { "El pago fue registrado antes de crear la promesa." }
            require(database.paymentReversals().findByOriginalOperationId(operationId) == null) { "No se puede vincular un pago reversado." }
            val loan = database.loans().findById(promise.loanId) ?: throw IllegalArgumentException("El préstamo de la promesa no existe.")
            require(loan.clientId == promise.clientId) { "El cliente de la promesa no coincide con el préstamo." }
            require(loan.currency == promise.currency) { "La moneda de la promesa no coincide con el préstamo." }
            val paymentRemaining = Math.subtractExact(payment.amountMinor, database.promisePaymentLinks().sumForPayment(operationId))
            require(paymentRemaining >= amountMinor) { "El importe supera el saldo disponible de ese pago." }
            val linkedToPromise = database.promisePaymentLinks().sumForPromise(promiseId)
            val promiseRemaining = Math.subtractExact(promise.promisedAmountMinor, linkedToPromise)
            require(promiseRemaining >= amountMinor) { "El importe supera el saldo pendiente de la promesa." }
            val linksDao = database.promisePaymentLinks()
            val existingLink = linksDao.findForPromiseAndPayment(promiseId, operationId)
            val link = if (existingLink == null) {
                PromisePaymentLinkEntity(promiseId, operationId, amountMinor, actor.staffId).also {
                    linksDao.insert(it)
                }
            } else {
                val accumulatedAmount = Math.addExact(existingLink.amountMinor, amountMinor)
                val updated = linksDao.updateAmountIfUnchanged(
                    promiseId = promiseId,
                    operationId = operationId,
                    expectedAmountMinor = existingLink.amountMinor,
                    newAmountMinor = accumulatedAmount
                )
                check(updated == 1) { "El vínculo de pago cambió durante la conciliación." }
                existingLink.copy(amountMinor = accumulatedAmount)
            }
            val newLinkedTotal = Math.addExact(linkedToPromise, amountMinor)
            val fullyKept = newLinkedTotal == promise.promisedAmountMinor
            if (fullyKept) {
                val changed = database.promisesToPay().closeOpenPromise(promiseId, PromiseToPayStatus.KEPT.name, promise.note)
                check(changed == 1) { "No se pudo cerrar la promesa con evidencia de pago." }
            }
            database.auditLogs().insert(AuditLogEntity(actorStaffId = actor.staffId, actorUsername = actor.username, action = "PROMISE_PAYMENT_LINKED", targetType = "PROMISE_TO_PAY", targetId = promiseId, details = "operationId=$operationId;amountMinor=$amountMinor;linkedTotal=$newLinkedTotal;fullyKept=$fullyKept"))
            link
        }
    }
    suspend fun resolve(
        actor: StaffSession,
        promiseId: String,
        status: PromiseToPayStatus,
        note: String = ""
    ): Boolean {
        require(status != PromiseToPayStatus.OPEN) { "Estado de cierre inválido." }
        require(status != PromiseToPayStatus.KEPT) {
            "Una promesa solo se marca cumplida al vincular pagos reales del libro contable."
        }
        require(authentication.isSessionValid(actor)) { "La sesión ha caducado." }
        require(authentication.hasPermission(actor, Permission.PROMISE_TO_PAY_RESOLVE)) {
            "No tienes permiso para cerrar promesas de pago."
        }
        return database.withTransaction {
            require(authentication.isSessionValid(actor)) { "La sesión ha caducado." }
            require(authentication.hasPermission(actor, Permission.PROMISE_TO_PAY_RESOLVE)) {
                "No tienes permiso para cerrar promesas de pago."
            }
            val existing = database.promisesToPay().findById(promiseId)
                ?: throw IllegalArgumentException("La promesa no existe.")
            if (actor.profile.role == com.infinityloan.security.StaffRole.COLLECTOR ||
                actor.profile.role == com.infinityloan.security.StaffRole.CASHIER
            ) {
                require(existing.collectorId == actor.staffId) {
                    "Solo puedes gestionar las promesas que registraste."
                }
            }
            val record = PromiseToPayRecord(
                promiseId = existing.promiseId,
                loanId = existing.loanId,
                clientId = existing.clientId,
                collectorId = existing.collectorId,
                promisedAmountMinor = existing.promisedAmountMinor,
                currency = existing.currency,
                promisedDate = LocalDate.parse(existing.promisedDate),
                status = PromiseToPayStatus.valueOf(existing.status),
                note = existing.note
            )
            val changed = PromiseToPayWorkflow.changeStatus(record, status, note)
            val rows = database.promisesToPay().closeOpenPromise(
                promiseId = promiseId,
                newStatus = changed.status.name,
                note = changed.note
            )
            if (rows != 1) return@withTransaction false
            database.auditLogs().insert(AuditLogEntity(
                actorStaffId = actor.staffId,
                actorUsername = actor.username,
                action = "PROMISE_TO_PAY_RESOLVED",
                targetType = "PROMISE_TO_PAY",
                targetId = promiseId,
                details = "status=${changed.status.name};noteLength=${changed.note.length}"
            ))
            true
        }
    }
}
