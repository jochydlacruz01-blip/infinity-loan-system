package com.infinityloan.finance

import java.time.LocalDate

data class PromiseToPaySummary(
    val openCount: Int,
    val overdueCount: Int,
    val keptCount: Int,
    val brokenCount: Int,
    val openMinorByCurrency: Map<String, Long>,
    val overdueMinorByCurrency: Map<String, Long>
)

object PromiseToPaySummaryCalculator {
    private val currencies = setOf("DOP", "USD", "EUR")
    fun calculate(records: List<PromiseToPayRecord>, asOf: LocalDate): PromiseToPaySummary {
        records.forEach { PromiseToPayWorkflow.validate(it) }
        require(records.map { it.promiseId }.distinct().size == records.size) { "Promesas duplicadas" }
        val open = records.filter { it.status == PromiseToPayStatus.OPEN }
        val overdue = open.filter { it.promisedDate.isBefore(asOf) }
        fun totals(list: List<PromiseToPayRecord>): Map<String, Long> =
            currencies.associateWith { currency ->
                list.filter { it.currency == currency }.fold(0L) { total, p ->
                    Math.addExact(total, p.promisedAmountMinor)
                }
            }
        return PromiseToPaySummary(
            openCount=open.size, overdueCount=overdue.size,
            keptCount=records.count { it.status == PromiseToPayStatus.KEPT },
            brokenCount=records.count { it.status == PromiseToPayStatus.BROKEN },
            openMinorByCurrency=totals(open), overdueMinorByCurrency=totals(overdue)
        )
    }
}