package com.infinityloan.finance

import java.time.LocalDate

data class CollectionVisitDaySummary(
    val scheduled: Int,
    val attempted: Int,
    val promises: Int,
    val completed: Int,
    val cancelled: Int
)

object CollectionVisitPlanner {
    fun forDay(records: List<CollectionVisitRecord>, date: LocalDate): List<CollectionVisitRecord> =
        records.filter { it.plannedDate == date && it.status != CollectionVisitStatus.CANCELLED }
            .sortedWith(compareBy<CollectionVisitRecord> { it.status != CollectionVisitStatus.SCHEDULED }
                .thenBy { it.collectorId.lowercase() }.thenBy { it.clientId.lowercase() }.thenBy { it.visitId })

    fun forCollector(records: List<CollectionVisitRecord>, collectorId: String, from: LocalDate, through: LocalDate): List<CollectionVisitRecord> {
        require(collectorId.isNotBlank()) { "Cobrador obligatorio" }
        require(!through.isBefore(from)) { "Rango de fechas inválido" }
        return records.filter { it.collectorId == collectorId && !it.plannedDate.isBefore(from) && !it.plannedDate.isAfter(through) && it.status != CollectionVisitStatus.CANCELLED }
            .sortedWith(compareBy<CollectionVisitRecord> { it.plannedDate }.thenBy { it.clientId.lowercase() }.thenBy { it.visitId })
    }

    fun summarize(records: List<CollectionVisitRecord>, date: LocalDate): CollectionVisitDaySummary {
        val day = records.filter { it.plannedDate == date }
        return CollectionVisitDaySummary(
            scheduled = day.count { it.status == CollectionVisitStatus.SCHEDULED },
            attempted = day.count { it.status == CollectionVisitStatus.ATTEMPTED || it.status == CollectionVisitStatus.UNREACHABLE },
            promises = day.count { it.status == CollectionVisitStatus.PROMISE_TO_PAY },
            completed = day.count { it.status == CollectionVisitStatus.PAID },
            cancelled = day.count { it.status == CollectionVisitStatus.CANCELLED }
        )
    }
}
