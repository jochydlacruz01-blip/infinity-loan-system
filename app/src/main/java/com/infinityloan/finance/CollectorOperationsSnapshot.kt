package com.infinityloan.finance

import java.time.LocalDate

data class CollectorOperationsSnapshot(
    val collectorId: String,
    val scheduledVisits: Int,
    val activeVisits: Int,
    val completedVisits: Int,
    val cancelledVisits: Int,
    val overduePromises: Int,
    val criticalPromises: Int,
    val overdueAmountMinorByCurrency: Map<String, Long>,
    val priorityScore: Int
)

/**
 * Read-only operational overview that combines visits planned for a date with missed
 * open promises. It ranks operational pressure, not employee performance.
 */
object CollectorOperationsSnapshotBuilder {
    private val currencies = listOf("DOP", "USD", "EUR")

    fun build(
        visits: Iterable<CollectionVisitRecord>,
        overduePromises: Iterable<PromiseFollowUpItem>,
        date: LocalDate
    ): List<CollectorOperationsSnapshot> {
        val visitRows = visits.toList()
        val promiseRows = overduePromises.toList()
        require(visitRows.map { it.visitId }.distinct().size == visitRows.size) { "Visitas duplicadas" }
        require(promiseRows.map { it.promiseId }.distinct().size == promiseRows.size) { "Promesas duplicadas" }
        visitRows.forEach(CollectionVisitWorkflow::validate)
        promiseRows.forEach {
            require(it.collectorId.isNotBlank() && it.amountMinor > 0 && it.currency in currencies)
        }
        val collectors = (visitRows.map { it.collectorId } + promiseRows.map { it.collectorId }).distinct().sorted()
        return collectors.map { id ->
            val day = visitRows.filter { it.collectorId == id && it.plannedDate == date }
            val active = day.count { it.status in setOf(CollectionVisitStatus.ATTEMPTED, CollectionVisitStatus.CONTACTED, CollectionVisitStatus.PROMISE_TO_PAY, CollectionVisitStatus.UNREACHABLE) }
            val promises = promiseRows.filter { it.collectorId == id }
            val totals = currencies.associateWith { currency ->
                promises.filter { it.currency == currency }.fold(0L) { sum, row -> Math.addExact(sum, row.amountMinor) }
            }
            val overdue = promises.size
            val critical = promises.count { it.urgency == PromiseFollowUpUrgency.CRITICAL }
            val scheduled = day.count { it.status == CollectionVisitStatus.SCHEDULED }
            val completed = day.count { it.status == CollectionVisitStatus.PAID }
            val cancelled = day.count { it.status == CollectionVisitStatus.CANCELLED }
            // Explicitly weighted, simple, reproducible workload pressure indicator.
            val score = Math.addExact(Math.multiplyExact(overdue, 3), Math.addExact(Math.multiplyExact(critical, 5), Math.addExact(scheduled, active)))
            CollectorOperationsSnapshot(id, scheduled, active, completed, cancelled, overdue, critical, totals, score)
        }.sortedWith(compareByDescending<CollectorOperationsSnapshot>{it.priorityScore}.thenBy{it.collectorId})
    }
}
