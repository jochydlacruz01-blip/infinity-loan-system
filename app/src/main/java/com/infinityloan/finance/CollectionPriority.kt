package com.infinityloan.finance

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CollectionPriorityLevel(val label: String) {
    CRITICAL("Crítica · 61+ días"),
    HIGH("Alta · 31–60 días"),
    MEDIUM("Media · 8–30 días"),
    LOW("Baja · 1–7 días")
}

data class CollectionPriorityRecord(
    val loanId: String,
    val clientId: String,
    val currency: String,
    val dueDate: LocalDate,
    val unpaidMinor: Long
)

data class CollectionPriorityItem(
    val loanId: String,
    val clientId: String,
    val currency: String,
    val dueDate: LocalDate,
    val daysOverdue: Long,
    val unpaidMinor: Long,
    val level: CollectionPriorityLevel
)

/**
 * Deterministic collection queue, prioritizing the oldest overdue installments.
 * Monetary amounts remain grouped by currency and are never converted.
 */
object CollectionPriority {
    fun build(records: Iterable<CollectionPriorityRecord>, asOfDate: LocalDate): List<CollectionPriorityItem> {
        val eligible = records.mapNotNull { row ->
            require(row.loanId.isNotBlank() && row.clientId.isNotBlank()) { "Identificadores obligatorios" }
            require(row.currency in setOf("DOP", "USD", "EUR")) { "Moneda no admitida: ${row.currency}" }
            require(row.unpaidMinor >= 0) { "El saldo no puede ser negativo" }
            if (row.unpaidMinor == 0L || !row.dueDate.isBefore(asOfDate)) return@mapNotNull null
            val days = ChronoUnit.DAYS.between(row.dueDate, asOfDate)
            val level = when {
                days >= 61 -> CollectionPriorityLevel.CRITICAL
                days >= 31 -> CollectionPriorityLevel.HIGH
                days >= 8 -> CollectionPriorityLevel.MEDIUM
                else -> CollectionPriorityLevel.LOW
            }
            CollectionPriorityItem(row.loanId, row.clientId, row.currency, row.dueDate, days, row.unpaidMinor, level)
        }
        return eligible.sortedWith(
            compareByDescending<CollectionPriorityItem> { it.daysOverdue }
                .thenByDescending { it.unpaidMinor }
                .thenBy { it.currency }
                .thenBy { it.clientId }
                .thenBy { it.loanId }
        )
    }
}
