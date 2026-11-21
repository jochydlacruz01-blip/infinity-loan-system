package com.infinityloan.finance

import java.time.LocalDate
import java.util.UUID

enum class CollectionVisitStatus {
    SCHEDULED, ATTEMPTED, CONTACTED, PROMISE_TO_PAY, PAID, UNREACHABLE, CANCELLED
}

data class CollectionVisitRecord(
    val visitId: String = UUID.randomUUID().toString(),
    val loanId: String,
    val clientId: String,
    val collectorId: String,
    val plannedDate: LocalDate,
    val status: CollectionVisitStatus = CollectionVisitStatus.SCHEDULED,
    val outcomeNote: String = ""
)

object CollectionVisitWorkflow {
    fun validate(record: CollectionVisitRecord) {
        require(record.visitId.isNotBlank()) { "Identificador de visita obligatorio" }
        require(record.loanId.isNotBlank() && record.clientId.isNotBlank()) { "Cliente y préstamo obligatorios" }
        require(record.collectorId.isNotBlank()) { "Debe asignarse un cobrador" }
        require(record.outcomeNote.length <= 2000) { "La nota excede el límite" }
    }

    fun transition(record: CollectionVisitRecord, next: CollectionVisitStatus, note: String = record.outcomeNote): CollectionVisitRecord {
        validate(record)
        val normalizedNote = note.trim()
        require(normalizedNote.length <= 2000) { "La nota excede el límite" }
        val allowed = when (record.status) {
            CollectionVisitStatus.SCHEDULED -> setOf(CollectionVisitStatus.ATTEMPTED, CollectionVisitStatus.CONTACTED, CollectionVisitStatus.CANCELLED, CollectionVisitStatus.UNREACHABLE)
            CollectionVisitStatus.ATTEMPTED -> setOf(CollectionVisitStatus.CONTACTED, CollectionVisitStatus.PROMISE_TO_PAY, CollectionVisitStatus.PAID, CollectionVisitStatus.UNREACHABLE, CollectionVisitStatus.CANCELLED)
            CollectionVisitStatus.CONTACTED -> setOf(CollectionVisitStatus.PROMISE_TO_PAY, CollectionVisitStatus.PAID, CollectionVisitStatus.UNREACHABLE, CollectionVisitStatus.CANCELLED)
            CollectionVisitStatus.PROMISE_TO_PAY -> setOf(CollectionVisitStatus.PAID, CollectionVisitStatus.ATTEMPTED, CollectionVisitStatus.UNREACHABLE, CollectionVisitStatus.CANCELLED)
            CollectionVisitStatus.UNREACHABLE -> setOf(CollectionVisitStatus.ATTEMPTED, CollectionVisitStatus.CONTACTED, CollectionVisitStatus.CANCELLED)
            CollectionVisitStatus.PAID, CollectionVisitStatus.CANCELLED -> emptySet()
        }
        require(next in allowed) { "Transición no permitida: ${record.status} → $next" }
        return record.copy(status = next, outcomeNote = normalizedNote)
    }
}
