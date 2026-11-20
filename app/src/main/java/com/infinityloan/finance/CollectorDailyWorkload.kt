package com.infinityloan.finance

import java.time.LocalDate

/** Operational workload view; ordering is deterministic and does not imply geographic routing. */
data class CollectorDailyWorkload(
    val collectorId: String,
    val date: LocalDate,
    val total: Int,
    val pending: Int,
    val inProgress: Int,
    val completed: Int,
    val cancelled: Int,
    val overCapacity: Boolean,
    val visitIds: List<String>
)

object CollectorDailyWorkloadBuilder {
    fun build(records: Iterable<CollectionVisitRecord>, date: LocalDate, capacityPerCollector: Int = 25): List<CollectorDailyWorkload> {
        require(capacityPerCollector > 0) { "La capacidad debe ser mayor que cero" }
        val day = records.filter { it.plannedDate == date }
        day.forEach(CollectionVisitWorkflow::validate)
        require(day.map { it.visitId }.distinct().size == day.size) { "IDs de visita duplicados" }
        return day.groupBy { it.collectorId }.map { (collector, rows) ->
            val active = rows.filter { it.status != CollectionVisitStatus.CANCELLED }
            CollectorDailyWorkload(
                collectorId = collector,
                date = date,
                total = active.size,
                pending = active.count { it.status == CollectionVisitStatus.SCHEDULED },
                inProgress = active.count { it.status in setOf(CollectionVisitStatus.ATTEMPTED, CollectionVisitStatus.CONTACTED, CollectionVisitStatus.PROMISE_TO_PAY, CollectionVisitStatus.UNREACHABLE) },
                completed = active.count { it.status == CollectionVisitStatus.PAID },
                cancelled = rows.count { it.status == CollectionVisitStatus.CANCELLED },
                overCapacity = active.size > capacityPerCollector,
                visitIds = active.sortedWith(compareBy<CollectionVisitRecord> { it.status != CollectionVisitStatus.SCHEDULED }.thenBy { it.clientId.lowercase() }.thenBy { it.visitId }).map { it.visitId }
            )
        }.sortedWith(compareByDescending<CollectorDailyWorkload> { it.overCapacity }.thenByDescending { it.total }.thenBy { it.collectorId.lowercase() })
    }
}
