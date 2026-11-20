package com.infinityloan.finance

import java.time.Instant

enum class CollectionContactChannel { IN_PERSON, PHONE, SMS, WHATSAPP, EMAIL, OTHER }
enum class CollectionContactOutcome { CONTACTED, NO_ANSWER, WRONG_NUMBER, REFUSED, PROMISE_TO_PAY, PARTIAL_PAYMENT, FOLLOW_UP_REQUIRED }

data class CollectionContactAttempt(
    val attemptId: String,
    val visitId: String,
    val loanId: String,
    val clientId: String,
    val collectorId: String,
    val occurredAt: Instant,
    val channel: CollectionContactChannel,
    val outcome: CollectionContactOutcome,
    val note: String = ""
)

data class ContactAttemptSummary(
    val collectorId: String,
    val attemptCount: Int,
    val successfulContacts: Int,
    val noAnswerCount: Int,
    val promises: Int,
    val followUps: Int
)

object CollectionContactAttemptLog {
    fun validate(attempt: CollectionContactAttempt) {
        require(attempt.attemptId.isNotBlank()) { "ID del intento obligatorio" }
        require(attempt.visitId.isNotBlank() && attempt.loanId.isNotBlank() && attempt.clientId.isNotBlank()) { "Visita, préstamo y cliente obligatorios" }
        require(attempt.collectorId.isNotBlank()) { "Cobrador obligatorio" }
        require(attempt.note.length <= 2000) { "La nota excede el límite" }
    }

    fun summarize(attempts: List<CollectionContactAttempt>): List<ContactAttemptSummary> {
        attempts.forEach(::validate)
        require(attempts.map { it.attemptId }.distinct().size == attempts.size) { "IDs de intento duplicados" }
        return attempts.groupBy { it.collectorId }.toSortedMap().map { (collector, rows) ->
            ContactAttemptSummary(
                collectorId = collector,
                attemptCount = rows.size,
                successfulContacts = rows.count { it.outcome in setOf(CollectionContactOutcome.CONTACTED, CollectionContactOutcome.PROMISE_TO_PAY, CollectionContactOutcome.PARTIAL_PAYMENT) },
                noAnswerCount = rows.count { it.outcome == CollectionContactOutcome.NO_ANSWER },
                promises = rows.count { it.outcome == CollectionContactOutcome.PROMISE_TO_PAY },
                followUps = rows.count { it.outcome == CollectionContactOutcome.FOLLOW_UP_REQUIRED }
            )
        }.sortedWith(compareByDescending<ContactAttemptSummary> { it.attemptCount }.thenBy { it.collectorId })
    }

    fun historyForVisit(attempts: List<CollectionContactAttempt>, visitId: String): List<CollectionContactAttempt> {
        require(visitId.isNotBlank()) { "Visita obligatoria" }
        attempts.forEach(::validate)
        require(attempts.map { it.attemptId }.distinct().size == attempts.size) { "IDs de intento duplicados" }
        return attempts.filter { it.visitId == visitId }.sortedWith(compareBy<CollectionContactAttempt> { it.occurredAt }.thenBy { it.attemptId })
    }
}
