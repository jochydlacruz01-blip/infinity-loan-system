package com.infinityloan.finance

data class PromiseFollowUpCollectorSummary(
    val collectorId: String,
    val overdueCount: Int,
    val criticalCount: Int,
    val highCount: Int,
    val mediumCount: Int,
    val lowCount: Int,
    val overdueMinorByCurrency: Map<String, Long>
)

/** Summarizes the missed-promise queue without combining monetary currencies. */
object PromiseFollowUpCollectorSummaryBuilder {
    private val currencies = listOf("DOP", "USD", "EUR")
    fun build(items: Iterable<PromiseFollowUpItem>): List<PromiseFollowUpCollectorSummary> {
        val rows = items.toList()
        require(rows.map { it.promiseId }.distinct().size == rows.size) { "Promesas duplicadas" }
        rows.forEach {
            require(it.collectorId.isNotBlank() && it.amountMinor > 0)
            require(it.currency in currencies)
        }
        return rows.groupBy { it.collectorId }.map { (collector, grouped) ->
            val totals = currencies.associateWith { currency ->
                grouped.filter { it.currency == currency }.fold(0L) { sum, row -> Math.addExact(sum, row.amountMinor) }
            }
            PromiseFollowUpCollectorSummary(
                collectorId=collector, overdueCount=grouped.size,
                criticalCount=grouped.count{it.urgency==PromiseFollowUpUrgency.CRITICAL},
                highCount=grouped.count{it.urgency==PromiseFollowUpUrgency.HIGH},
                mediumCount=grouped.count{it.urgency==PromiseFollowUpUrgency.MEDIUM},
                lowCount=grouped.count{it.urgency==PromiseFollowUpUrgency.LOW},
                overdueMinorByCurrency=totals
            )
        }.sortedWith(compareByDescending<PromiseFollowUpCollectorSummary>{it.criticalCount}
            .thenByDescending{it.overdueCount}.thenBy{it.collectorId})
    }
}
