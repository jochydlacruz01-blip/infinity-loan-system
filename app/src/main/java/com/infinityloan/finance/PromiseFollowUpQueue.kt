package com.infinityloan.finance

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class PromiseFollowUpUrgency(val label: String) {
    CRITICAL("Crítica · 30+ días"),
    HIGH("Alta · 15–29 días"),
    MEDIUM("Media · 7–14 días"),
    LOW("Baja · 1–6 días")
}

data class PromiseFollowUpItem(
    val promiseId: String,
    val loanId: String,
    val clientId: String,
    val collectorId: String,
    val currency: String,
    val amountMinor: Long,
    val promisedDate: LocalDate,
    val daysOverdue: Long,
    val urgency: PromiseFollowUpUrgency
)

/** Builds a deterministic follow-up queue for missed open promises; amounts stay currency-separated. */
object PromiseFollowUpQueue {
    fun build(records: Iterable<PromiseToPayRecord>, asOf: LocalDate): List<PromiseFollowUpItem> {
        val seen = mutableSetOf<String>()
        return records.mapNotNull { p ->
            PromiseToPayWorkflow.validate(p)
            require(seen.add(p.promiseId)) { "Promesas duplicadas" }
            if (p.status != PromiseToPayStatus.OPEN || !p.promisedDate.isBefore(asOf)) return@mapNotNull null
            val days = ChronoUnit.DAYS.between(p.promisedDate, asOf)
            val urgency = when {
                days >= 30 -> PromiseFollowUpUrgency.CRITICAL
                days >= 15 -> PromiseFollowUpUrgency.HIGH
                days >= 7 -> PromiseFollowUpUrgency.MEDIUM
                else -> PromiseFollowUpUrgency.LOW
            }
            PromiseFollowUpItem(p.promiseId,p.loanId,p.clientId,p.collectorId,p.currency,p.promisedAmountMinor,p.promisedDate,days,urgency)
        }.sortedWith(compareByDescending<PromiseFollowUpItem>{it.daysOverdue}
            .thenBy{it.currency}.thenByDescending{it.amountMinor}.thenBy{it.clientId}.thenBy{it.promiseId})
    }
}
