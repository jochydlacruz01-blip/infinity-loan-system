package com.infinityloan.finance

data class CollectorVisitPrioritySummary(
    val collectorId: String,
    val overdueCount: Int,
    val dueTodayCount: Int,
    val upcomingCount: Int,
    val overdueDaysMax: Long,
    val totalOpenVisits: Int
)
data class CollectionPriorityDashboard(
    val overdueTotal: Int,
    val dueTodayTotal: Int,
    val upcomingTotal: Int,
    val collectorCount: Int,
    val collectors: List<CollectorVisitPrioritySummary>
)

/** Supervisor-oriented aggregation of the date-prioritized queue, without location tracking. */
object CollectionPriorityDashboardBuilder {
    fun build(items: Iterable<PrioritizedCollectionVisit>): CollectionPriorityDashboard {
        val rows=items.toList()
        require(rows.map { it.record.visitId }.distinct().size==rows.size) { "Visitas duplicadas" }
        rows.forEach { require(it.record.collectorId.isNotBlank() && it.daysLate>=0) }
        val collectors=rows.groupBy { it.record.collectorId }.map { (id,visits) ->
            val overdue=visits.filter { it.priority==CollectionVisitPriority.OVERDUE }
            CollectorVisitPrioritySummary(id,overdue.size,
                visits.count { it.priority==CollectionVisitPriority.DUE_TODAY },
                visits.count { it.priority==CollectionVisitPriority.UPCOMING },
                overdue.maxOfOrNull { it.daysLate } ?: 0L,visits.size)
        }.sortedWith(compareByDescending<CollectorVisitPrioritySummary>{it.overdueCount}
            .thenByDescending{it.overdueDaysMax}.thenByDescending{it.dueTodayCount}.thenBy{it.collectorId})
        return CollectionPriorityDashboard(
            rows.count { it.priority==CollectionVisitPriority.OVERDUE },
            rows.count { it.priority==CollectionVisitPriority.DUE_TODAY },
            rows.count { it.priority==CollectionVisitPriority.UPCOMING },
            collectors.size,collectors)
    }
}
