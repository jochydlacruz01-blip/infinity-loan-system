package com.infinityloan.finance

import java.time.LocalDate

data class CollectorPerformanceDetail(
    val collectorId: String,
    val dateFrom: LocalDate,
    val dateThrough: LocalDate,
    val scheduled: Int,
    val cancelled: Int,
    val completedVisits: Int,
    val contactAttempts: Int,
    val successfulContacts: Int,
    val promisesToPay: Int,
    val paidVisits: Int,
    val unreachableVisits: Int,
    val contactRatePercent: Int,
    val promiseConversionPercent: Int,
    val paidVisitRatePercent: Int
)

/** Detailed, deterministic visit performance metrics for staff reports. */
object CollectorPerformanceDetails {
    fun build(records: List<CollectionVisitRecord>, from: LocalDate, through: LocalDate): List<CollectorPerformanceDetail> {
        require(!through.isBefore(from)) { "Rango de fechas inválido" }
        val inRange = records.filter { !it.plannedDate.isBefore(from) && !it.plannedDate.isAfter(through) }
        return inRange.groupBy { it.collectorId }.toSortedMap().map { (collectorId, visits) ->
            require(collectorId.isNotBlank()) { "Cobrador obligatorio" }
            val scheduled = visits.count { it.status != CollectionVisitStatus.CANCELLED }
            val cancelled = visits.count { it.status == CollectionVisitStatus.CANCELLED }
            val attempts = visits.count { it.status == CollectionVisitStatus.ATTEMPTED }
            val contacted = visits.count { it.status == CollectionVisitStatus.CONTACTED || it.status == CollectionVisitStatus.PROMISE_TO_PAY || it.status == CollectionVisitStatus.PAID }
            val promises = visits.count { it.status == CollectionVisitStatus.PROMISE_TO_PAY }
            val paid = visits.count { it.status == CollectionVisitStatus.PAID }
            val unreachable = visits.count { it.status == CollectionVisitStatus.UNREACHABLE }
            val contactBase = attempts + contacted + unreachable
            val completed = visits.count { it.status != CollectionVisitStatus.SCHEDULED && it.status != CollectionVisitStatus.CANCELLED }
            val promiseBase = contacted
            CollectorPerformanceDetail(
                collectorId, from, through, scheduled, cancelled, completed, attempts,
                contacted, promises, paid, unreachable,
                if (contactBase == 0) 0 else contacted * 100 / contactBase,
                if (promiseBase == 0) 0 else promises * 100 / promiseBase,
                if (scheduled == 0) 0 else paid * 100 / scheduled
            )
        }.sortedWith(compareByDescending<CollectorPerformanceDetail> { it.paidVisits }
            .thenByDescending { it.promisesToPay }.thenBy { it.collectorId })
    }
}
