package com.infinityloan.data

import androidx.room.withTransaction
import com.infinityloan.finance.PromiseToPayRecord
import com.infinityloan.finance.PromiseToPayStatus
import com.infinityloan.finance.PromiseToPayWorkflow
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
            val loan = database.loans().findById(loanId)
                ?: throw IllegalArgumentException("El préstamo no existe.")
            require(loan.status == "ACTIVE") { "El préstamo no está activo." }
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

    suspend fun resolve(
        actor: StaffSession,
        promiseId: String,
        status: PromiseToPayStatus,
        note: String = ""
    ): Boolean {
        require(status != PromiseToPayStatus.OPEN) { "Estado de cierre inválido." }
        require(authentication.isSessionValid(actor)) { "La sesión ha caducado." }
        require(authentication.hasPermission(actor, Permission.PROMISE_TO_PAY_RESOLVE)) {
            "No tienes permiso para cerrar promesas de pago."
        }
        return database.withTransaction {
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
