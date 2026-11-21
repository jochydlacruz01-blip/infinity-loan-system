package com.infinityloan.finance

import java.time.LocalDate

data class CollectorVisitPerformance(
    val collectorId: String,
    val planned: Int,
    val completed: Int,
    val attempted: Int,
    val contacted: Int,
    val promises: Int,
    val paid: Int,
    val unreachable: Int,
    val cancelled: Int,
    val completionRatePercent: Int,
    val paidRatePercent: Int,
    val promiseRatePercent: Int
)

/** Aggregates persisted visit outcomes by collector for a bounded planned-date range. */
object CollectionVisitPerformance {
    fun summarize(records: List<CollectionVisitRecord>, from: LocalDate, through: LocalDate): List<CollectorVisitPerformance> {
        require(!through.isBefore(from)) { "Rango de fechas inválido" }
        val rows = records.filter { !it.plannedDate.isBefore(from) && !it.plannedDate.isAfter(through) }
        return rows.groupBy { it.collectorId }.toSortedMap().map { (collector, visits) ->
            require(collector.isNotBlank()) { "Cobrador obligatorio" }
            val cancelled = visits.count { it.status == CollectionVisitStatus.CANCELLED }
            val planned = visits.size - cancelled
            val attempted = visits.count { it.status == CollectionVisitStatus.ATTEMPTED }
            val contacted = visits.count { it.status == CollectionVisitStatus.CONTACTED }
            val promises = visits.count { it.status == CollectionVisitStatus.PROMISE_TO_PAY }
            val paid = visits.count { it.status == CollectionVisitStatus.PAID }
            val unreachable = visits.count { it.status == CollectionVisitStatus.UNREACHABLE }
            val completed = attempted + contacted + promises + paid + unreachable
            val completion = if (planned == 0) 0 else completed * 100 / planned
            val paidRate = if (planned == 0) 0 else paid * 100 / planned
            val promiseDenominator = completed
            val promiseRate = if (promiseDenominator == 0) 0 else promises * 100 / promiseDenominator
            CollectorVisitPerformance(
                collector, planned, completed, attempted, contacted, promises, paid,
                unreachable, cancelled, completion, paidRate, promiseRate
            )
        }.sortedWith(
            compareByDescending<CollectorVisitPerformance> { it.completed }
                .thenByDescending { it.paid }
                .thenBy { it.collectorId }
        )
    }
}
