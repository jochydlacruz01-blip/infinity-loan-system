package com.infinityloan.finance

import java.time.LocalDate

data class CollectorVisitPerformance(
    val collectorId: String,
    val planned: Int,
    val attempted: Int,
    val contacted: Int,
    val promises: Int,
    val paid: Int,
    val unreachable: Int,
    val cancelled: Int,
    val completionRatePercent: Int,
    val promiseRatePercent: Int
)

/** Aggregates visit outcomes by collector for a bounded date range, excluding no data silently. */
object CollectionVisitPerformance {
    fun summarize(records: List<CollectionVisitRecord>, from: LocalDate, through: LocalDate): List<CollectorVisitPerformance> {
        require(!through.isBefore(from)) { "Rango de fechas inválido" }
        val rows = records.filter { !it.plannedDate.isBefore(from) && !it.plannedDate.isAfter(through) }
        return rows.groupBy { it.collectorId }.toSortedMap().map { (collector, visits) ->
            require(collector.isNotBlank()) { "Cobrador obligatorio" }
            val planned = visits.size - visits.count { it.status == CollectionVisitStatus.CANCELLED }
            val attempted = visits.count { it.status == CollectionVisitStatus.ATTEMPTED }
            val contacted = visits.count { it.status == CollectionVisitStatus.CONTACTED }
            val promises = visits.count { it.status == CollectionVisitStatus.PROMISE_TO_PAY }
            val paid = visits.count { it.status == CollectionVisitStatus.PAID }
            val unreachable = visits.count { it.status == CollectionVisitStatus.UNREACHABLE }
            val cancelled = visits.count { it.status == CollectionVisitStatus.CANCELLED }
            val denominator = planned
            val completion = if (denominator == 0) 0 else (paid * 100 / denominator)
            val promiseDenominator = attempted + contacted + promises + paid + unreachable
            val promiseRate = if (promiseDenominator == 0) 0 else (promises * 100 / promiseDenominator)
            CollectorVisitPerformance(collector, planned, attempted, contacted, promises, paid, unreachable, cancelled, completion, promiseRate)
        }.sortedWith(compareByDescending<CollectorVisitPerformance> { it.paid }.thenByDescending { it.promises }.thenBy { it.collectorId })
    }
}
