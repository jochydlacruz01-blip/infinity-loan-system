package com.infinityloan.finance

import java.time.LocalDate

data class PromiseMonitoringItem(
    val promiseId: String,
    val clientId: String,
    val collectorId: String,
    val amountMinor: Long,
    val currency: String,
    val promisedDate: LocalDate,
    val status: PromiseToPayStatus,
    val daysUntilOrOverdue: Long
)

data class PromiseMonitoringSummary(
    val overdue: Int,
    val dueToday: Int,
    val upcoming: Int,
    val currencyTotalsMinor: Map<String, Long>,
    val items: List<PromiseMonitoringItem>
)

/** Monitoring of open promises due today, overdue, or in a configurable future horizon. */
object PromiseMonitoringBuilder {
    fun build(records: Iterable<PromiseToPayRecord>, asOf: LocalDate, upcomingDays: Long = 7): PromiseMonitoringSummary {
        require(upcomingDays >= 0) { "Horizonte inválido" }
        val seen = mutableSetOf<String>()
        val items = records.mapNotNull { p ->
            PromiseToPayWorkflow.validate(p)
            require(seen.add(p.promiseId)) { "Promesas duplicadas" }
            if (p.status != PromiseToPayStatus.OPEN) return@mapNotNull null
            val delta = java.time.temporal.ChronoUnit.DAYS.between(asOf, p.promisedDate)
            if (delta > upcomingDays) return@mapNotNull null
            PromiseMonitoringItem(p.promiseId,p.clientId,p.collectorId,p.promisedAmountMinor,p.currency,p.promisedDate,p.status,delta)
        }.sortedWith(compareBy<PromiseMonitoringItem> { it.daysUntilOrOverdue }.thenBy { it.currency }.thenBy { it.clientId }.thenBy { it.promiseId })
        val overdue = items.count { it.daysUntilOrOverdue < 0 }
        val today = items.count { it.daysUntilOrOverdue == 0L }
        val upcoming = items.count { it.daysUntilOrOverdue > 0 }
        val totals=items.groupBy { it.currency }.mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }.toSortedMap()
        return PromiseMonitoringSummary(overdue,today,upcoming,totals,items)
    }
}
