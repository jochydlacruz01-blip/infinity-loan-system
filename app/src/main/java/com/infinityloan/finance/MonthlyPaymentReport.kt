package com.infinityloan.finance

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Currency-aware, month-bucketed reporting input; amount is stored in minor units. */
data class PaymentReportEntry(
    val operationId: String,
    val amountMinor: Long,
    val currency: String,
    val paidAtMillis: Long
)

data class MonthlyPaymentTotal(val month: YearMonth, val currency: String, val totalMinor: Long, val count: Int)

object MonthlyPaymentReport {
    private val supportedCurrencies = setOf("DOP", "USD", "EUR")

    fun aggregate(entries: List<PaymentReportEntry>, zone: ZoneId = ZoneId.of("UTC")): List<MonthlyPaymentTotal> {
        require(entries.map { it.operationId }.distinct().size == entries.size) { "Operaciones duplicadas" }
        entries.forEach {
            require(it.operationId.isNotBlank()) { "Identificador obligatorio" }
            require(it.amountMinor >= 0L) { "Importe negativo inválido" }
            require(it.currency in supportedCurrencies) { "Moneda no soportada" }
        }
        return entries.groupBy { entry ->
            YearMonth.from(Instant.ofEpochMilli(entry.paidAtMillis).atZone(zone))
                to entry.currency
        }.map { (key, rows) ->
            MonthlyPaymentTotal(key.first, key.second,
                rows.fold(0L) { total, row -> Math.addExact(total, row.amountMinor) }, rows.size)
        }.sortedWith(compareBy<MonthlyPaymentTotal> { it.month }.thenBy { it.currency })
    }
}
