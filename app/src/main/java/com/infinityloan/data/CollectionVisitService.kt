package com.infinityloan.data

import androidx.room.withTransaction
import com.infinityloan.finance.CollectionVisitRecord
import com.infinityloan.finance.CollectionVisitAssignmentPolicy
import com.infinityloan.finance.CollectionVisitStatus
import com.infinityloan.finance.CollectionVisitWorkflow
import com.infinityloan.security.AccessPolicy
import com.infinityloan.security.Permission
import com.infinityloan.security.StaffAuthenticationService
import com.infinityloan.security.StaffRole
import com.infinityloan.security.StaffSession
import java.time.LocalDate

/**
 * Transactional persistence boundary for field collection visits.
 * Every mutation validates the live staff session and writes its audit event atomically.
 */
class CollectionVisitService(
    private val database: InfinityDatabase,
    private val authentication: StaffAuthenticationService
) {
    fun observe(actor: StaffSession) =
        if (AccessPolicy.allows(actor.profile, Permission.COLLECTION_VISIT_VIEW)) {
            if (actor.profile.role == StaffRole.COLLECTOR) {
                database.collectionVisits().observeForCollector(actor.staffId)
            } else database.collectionVisits().observeAll()
        } else throw SecurityException("No tienes permiso para consultar visitas.")

    suspend fun create(
        actor: StaffSession,
        loanId: String,
        plannedDate: LocalDate,
        note: String = "",
        collectorId: String = actor.staffId
    ): CollectionVisitEntity {
        require(authentication.isSessionValid(actor)) { "La sesión ha caducado." }
        require(authentication.hasPermission(actor, Permission.COLLECTION_VISIT_MANAGE)) {
            "No tienes permiso para programar visitas."
        }
        return database.withTransaction {
            val loan = database.loans().findById(loanId)
                ?: throw IllegalArgumentException("El préstamo no existe.")
            require(loan.status == "ACTIVE") { "El préstamo no está activo." }
            val target = if (actor.profile.role == StaffRole.COLLECTOR) null
                else database.staffAccounts().findById(collectorId)
            val assignedCollectorId = CollectionVisitAssignmentPolicy.resolve(
                actorRole = actor.profile.role,
                actorStaffId = actor.staffId,
                requestedCollectorId = collectorId,
                targetRole = target?.role,
                targetIsActive = target?.isActive == true
            )
            val record = CollectionVisitRecord(
                loanId = loan.id,
                clientId = loan.clientId,
                collectorId = assignedCollectorId,
                plannedDate = plannedDate,
                outcomeNote = note.trim()
            )
            CollectionVisitWorkflow.validate(record)
            val entity = CollectionVisitEntity(
                visitId = record.visitId,
                loanId = record.loanId,
                clientId = record.clientId,
                collectorId = record.collectorId,
                plannedDate = record.plannedDate.toString(),
                status = record.status.name,
                outcomeNote = record.outcomeNote
            )
            database.collectionVisits().insert(entity)
            database.auditLogs().insert(AuditLogEntity(
                actorStaffId = actor.staffId,
                actorUsername = actor.username,
                action = "COLLECTION_VISIT_CREATED",
                targetType = "COLLECTION_VISIT",
                targetId = entity.visitId,
                details = "loanId=${entity.loanId};plannedDate=${entity.plannedDate};collectorId=${entity.collectorId}"
            ))
            entity
        }
    }

    suspend fun changeStatus(
        actor: StaffSession,
        visitId: String,
        next: CollectionVisitStatus,
        note: String = ""
    ): Boolean {
        require(authentication.isSessionValid(actor)) { "La sesión ha caducado." }
        require(authentication.hasPermission(actor, Permission.COLLECTION_VISIT_MANAGE)) {
            "No tienes permiso para actualizar visitas."
        }
        return database.withTransaction {
            val existing = database.collectionVisits().findById(visitId)
                ?: throw IllegalArgumentException("La visita no existe.")
            if (actor.profile.role == StaffRole.COLLECTOR) {
                require(existing.collectorId == actor.staffId) {
                    "Solo puedes actualizar tus propias visitas."
                }
            }
            val record = CollectionVisitRecord(
                visitId = existing.visitId,
                loanId = existing.loanId,
                clientId = existing.clientId,
                collectorId = existing.collectorId,
                plannedDate = LocalDate.parse(existing.plannedDate),
                status = CollectionVisitStatus.valueOf(existing.status),
                outcomeNote = existing.outcomeNote
            )
            val changed = CollectionVisitWorkflow.transition(record, next, note)
            val rows = database.collectionVisits().transition(
                visitId = visitId,
                expectedStatus = existing.status,
                status = changed.status.name,
                note = changed.outcomeNote,
                updatedAt = System.currentTimeMillis()
            )
            if (rows != 1) return@withTransaction false
            database.auditLogs().insert(AuditLogEntity(
                actorStaffId = actor.staffId,
                actorUsername = actor.username,
                action = "COLLECTION_VISIT_STATUS_CHANGED",
                targetType = "COLLECTION_VISIT",
                targetId = visitId,
                details = "from=${existing.status};to=${changed.status.name};noteLength=${changed.outcomeNote.length}"
            ))
            true
        }
    }
    suspend fun markPaid(
        actor: StaffSession,
        visitId: String,
        paymentOperationId: String,
        note: String = ""
    ): Boolean {
        require(authentication.isSessionValid(actor)) { "La sesión ha caducado." }
        require(authentication.hasPermission(actor, Permission.COLLECTION_VISIT_MANAGE)) {
            "No tienes permiso para actualizar visitas."
        }
        require(paymentOperationId.isNotBlank()) { "Debe indicar el pago registrado." }
        return database.withTransaction {
            val existing = database.collectionVisits().findById(visitId)
                ?: throw IllegalArgumentException("La visita no existe.")
            if (actor.profile.role == StaffRole.COLLECTOR) {
                require(existing.collectorId == actor.staffId) {
                    "Solo puedes actualizar tus propias visitas."
                }
            }
            val payment = database.payments().findByOperationId(paymentOperationId)
                ?: throw IllegalArgumentException("El pago indicado no existe.")
            require(payment.amountMinor > 0L) { "El pago no tiene un importe válido." }
            val reversed = database.paymentReversals()
                .findByOriginalOperationId(paymentOperationId) != null
            val record = CollectionVisitRecord(
                visitId = existing.visitId,
                loanId = existing.loanId,
                clientId = existing.clientId,
                collectorId = existing.collectorId,
                plannedDate = LocalDate.parse(existing.plannedDate),
                status = CollectionVisitStatus.valueOf(existing.status),
                outcomeNote = existing.outcomeNote
            )
            val changed = CollectionVisitWorkflow.completeWithPayment(
                record = record,
                paymentMatchesLoan = payment.loanId == existing.loanId,
                paymentIsReversed = reversed,
                note = note
            )
            val rows = database.collectionVisits().transition(
                visitId = visitId,
                expectedStatus = existing.status,
                status = changed.status.name,
                note = changed.outcomeNote,
                updatedAt = System.currentTimeMillis()
            )
            if (rows != 1) return@withTransaction false
            database.auditLogs().insert(AuditLogEntity(
                actorStaffId = actor.staffId,
                actorUsername = actor.username,
                action = "COLLECTION_VISIT_MARKED_PAID",
                targetType = "COLLECTION_VISIT",
                targetId = visitId,
                details = "paymentOperationId=$paymentOperationId;loanId=${existing.loanId}"
            ))
            true
        }
    }
}
