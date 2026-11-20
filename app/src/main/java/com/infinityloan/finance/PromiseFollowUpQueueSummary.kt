package com.infinityloan.finance

data class PromiseFollowUpUrgencyTotals(
    val count: Int,
    val amountMinorByCurrency: Map<String, Long>
)
data class PromiseFollowUpQueueSummary(
    val total: Int,
    val urgency: Map<PromiseFollowUpUrgency, PromiseFollowUpUrgencyTotals>,
    val collectorCount: Int
)

/** Currency-safe summary of a follow-up queue. Each currency is accumulated separately. */
object PromiseFollowUpQueueSummaryBuilder {
    private val currencies=setOf("DOP","USD","EUR")
    fun build(items: Iterable<PromiseFollowUpItem>): PromiseFollowUpQueueSummary {
        val rows=items.toList()
        require(rows.map{it.promiseId}.distinct().size==rows.size) { "Promesas duplicadas" }
        rows.forEach { require(it.promiseId.isNotBlank() && it.collectorId.isNotBlank() && it.amountMinor>0 && it.currency in currencies) }
        val grouped=PromiseFollowUpUrgency.values().associateWith { urgency ->
            val subset=rows.filter{it.urgency==urgency}
            val totals=currencies.associateWith { currency -> subset.filter{it.currency==currency}.fold(0L){sum,row->Math.addExact(sum,row.amountMinor)} }
            PromiseFollowUpUrgencyTotals(subset.size,totals)
        }
        return PromiseFollowUpQueueSummary(rows.size,grouped,rows.map{it.collectorId}.distinct().size)
    }
}
