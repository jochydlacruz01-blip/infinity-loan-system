package com.infinityloan.finance

import java.time.Instant
import java.time.LocalDate

data class ClientCollectionDossier(
    val clientId: String,
    val loanIds: List<String>,
    val visits: List<CollectionVisitRecord>,
    val contactAttempts: List<CollectionContactAttempt>,
    val openPromises: List<PromiseToPayRecord>,
    val overduePromises: List<PromiseToPayRecord>,
    val openPromiseTotalsMinorByCurrency: Map<String, Long>,
    val lastContactAt: Instant?,
    val visitCount: Int,
    val contactCount: Int
)

/** Aggregates collection activity and open promises into a client-level dossier. */
object ClientCollectionDossierBuilder {
    fun build(
        clientId: String,
        visits: Iterable<CollectionVisitRecord>,
        attempts: Iterable<CollectionContactAttempt>,
        promises: Iterable<PromiseToPayRecord>,
        asOf: LocalDate
    ): ClientCollectionDossier {
        require(clientId.isNotBlank()) { "Cliente obligatorio" }
        val allVisits = visits.toList()
        allVisits.forEach(CollectionVisitWorkflow::validate)
        require(allVisits.map { it.visitId }.distinct().size == allVisits.size) { "Visitas duplicadas" }
        val allAttempts = attempts.toList()
        allAttempts.forEach(CollectionContactAttemptLog::validate)
        require(allAttempts.map { it.attemptId }.distinct().size == allAttempts.size) { "Intentos duplicados" }
        val allPromises = promises.toList()
        allPromises.forEach(PromiseToPayWorkflow::validate)
        require(allPromises.map { it.promiseId }.distinct().size == allPromises.size) { "Promesas duplicadas" }

        val clientVisits = allVisits.filter { it.clientId == clientId }
            .sortedWith(compareBy<CollectionVisitRecord> { it.plannedDate }.thenBy { it.visitId })
        val clientAttempts = allAttempts.filter { it.clientId == clientId }
            .sortedWith(compareBy<CollectionContactAttempt> { it.occurredAt }.thenBy { it.attemptId })
        val clientPromises = allPromises.filter { it.clientId == clientId && it.status == PromiseToPayStatus.OPEN }
            .sortedWith(compareBy<PromiseToPayRecord> { it.promisedDate }.thenBy { it.promiseId })
        val overdue = clientPromises.filter { it.promisedDate.isBefore(asOf) }
        val totals = clientPromises.groupBy { it.currency }.mapValues { (_, rows) -> rows.sumOf { it.promisedAmountMinor } }.toSortedMap()
        val loanIds = (clientVisits.map { it.loanId } + clientAttempts.map { it.loanId } + clientPromises.map { it.loanId }).distinct().sorted()
        return ClientCollectionDossier(
            clientId, loanIds, clientVisits, clientAttempts, clientPromises, overdue, totals,
            clientAttempts.lastOrNull()?.occurredAt, clientVisits.size, clientAttempts.size
        )
    }
}
