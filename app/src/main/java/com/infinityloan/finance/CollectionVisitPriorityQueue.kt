package com.infinityloan.finance

import java.time.LocalDate

enum class CollectionVisitPriority(val rank: Int) { OVERDUE(0), DUE_TODAY(1), UPCOMING(2) }

data class PrioritizedCollectionVisit(val record: CollectionVisitRecord, val priority: CollectionVisitPriority, val daysLate: Long)

/** Deterministic, date-based prioritization; does not claim geographic route optimization. */
object CollectionVisitPriorityQueue {
    fun build(records: Iterable<CollectionVisitRecord>, asOf: LocalDate): List<PrioritizedCollectionVisit> {
        val seen = mutableSetOf<String>()
        return records.mapNotNull { record ->
            CollectionVisitWorkflow.validate(record)
            require(seen.add(record.visitId)) { "Visitas duplicadas" }
            if (record.status == CollectionVisitStatus.PAID || record.status == CollectionVisitStatus.CANCELLED) return@mapNotNull null
            val late = if (record.plannedDate.isBefore(asOf)) java.time.temporal.ChronoUnit.DAYS.between(record.plannedDate, asOf) else 0L
            val priority = when {
                late > 0 -> CollectionVisitPriority.OVERDUE
                record.plannedDate == asOf -> CollectionVisitPriority.DUE_TODAY
                else -> CollectionVisitPriority.UPCOMING
            }
            PrioritizedCollectionVisit(record, priority, late)
        }.sortedWith(compareBy<PrioritizedCollectionVisit>{it.priority.rank}
            .thenByDescending{it.daysLate}.thenBy{it.record.plannedDate}
            .thenBy{it.record.collectorId}.thenBy{it.record.clientId}.thenBy{it.record.visitId})
    }
}
