package com.infinityloan.finance

import java.time.LocalDate

data class PromiseToPayOperationalRow(
    val collectorId: String,
    val totalOpen: Int,
    val overdue: Int,
    val dueToday: Int,
    val upcoming: Int,
    val openMinorByCurrency: Map<String, Long>,
    val overdueMinorByCurrency: Map<String, Long>
)

object PromiseToPayOperationalReport {
    private val currencies = listOf("DOP", "USD", "EUR")

    fun build(records: List<PromiseToPayRecord>, asOf: LocalDate): List<PromiseToPayOperationalRow> {
        records.forEach(PromiseToPayWorkflow::validate)
        require(records.map { it.promiseId }.distinct().size == records.size) { "Promesas duplicadas" }
        val open = records.filter { it.status == PromiseToPayStatus.OPEN }
        return open.groupBy { it.collectorId }.toSortedMap().map { (collector, promises) ->
            val overdue = promises.filter { it.promisedDate.isBefore(asOf) }
            val today = promises.filter { it.promisedDate == asOf }
            val upcoming = promises.filter { it.promisedDate.isAfter(asOf) }
            fun totals(list: List<PromiseToPayRecord>) = currencies.associateWith { currency ->
                list.filter { it.currency == currency }.fold(0L) { total, p -> Math.addExact(total, p.promisedAmountMinor) }
            }
            PromiseToPayOperationalRow(
                collectorId = collector,
                totalOpen = promises.size,
                overdue = overdue.size,
                dueToday = today.size,
                upcoming = upcoming.size,
                openMinorByCurrency = totals(promises),
                overdueMinorByCurrency = totals(overdue)
            )
        }.sortedWith(compareByDescending<PromiseToPayOperationalRow> { it.overdue }
            .thenByDescending { it.totalOpen }.thenBy { it.collectorId })
    }
}
