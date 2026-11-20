package com.infinityloan.finance

import java.time.LocalDate

enum class AgingBucket(val label: String) {
    DAYS_1_7("1–7 días"),
    DAYS_8_30("8–30 días"),
    DAYS_31_60("31–60 días"),
    DAYS_61_PLUS("61+ días")
}

data class AgingInstallment(val currency: String, val dueDate: LocalDate, val unpaidMinor: Long)
data class AgingBucketTotal(val bucket: AgingBucket, val installmentCount: Int, val unpaidMinor: Long)
data class CurrencyAgingSummary(val currency: String, val buckets: List<AgingBucketTotal>, val totalOverdueMinor: Long)

/** Deterministic aging analysis; only positive unpaid balances strictly past due are included. */
object PortfolioAging {
    fun build(rows: Iterable<AgingInstallment>, asOfDate: LocalDate): List<CurrencyAgingSummary> {
        val records = rows.toList()
        records.forEach {
            require(it.currency in setOf("DOP", "USD", "EUR")) { "Moneda no admitida: ${it.currency}" }
            require(it.unpaidMinor >= 0) { "El saldo vencido no puede ser negativo" }
        }
        return records.map { it.currency }.distinct().sorted().map { currency ->
            val eligible = records.filter { it.currency == currency && it.unpaidMinor > 0 && it.dueDate.isBefore(asOfDate) }
            val bucketTotals = AgingBucket.entries.map { bucket ->
                val matches = eligible.filter { row ->
                    val days = java.time.temporal.ChronoUnit.DAYS.between(row.dueDate, asOfDate)
                    when(bucket) {
                        AgingBucket.DAYS_1_7 -> days in 1..7
                        AgingBucket.DAYS_8_30 -> days in 8..30
                        AgingBucket.DAYS_31_60 -> days in 31..60
                        AgingBucket.DAYS_61_PLUS -> days >= 61
                    }
                }
                AgingBucketTotal(bucket, matches.size, matches.fold(0L) { acc, row -> Math.addExact(acc, row.unpaidMinor) })
            }
            CurrencyAgingSummary(currency, bucketTotals, bucketTotals.fold(0L) { acc, row -> Math.addExact(acc, row.unpaidMinor) })
        }
    }
}
