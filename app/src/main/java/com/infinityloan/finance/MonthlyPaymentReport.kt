package com.infinityloan.finance

import com.infinityloan.data.PaymentEntity
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
    /**
     * Builds monthly totals from persisted payments. Currency is resolved through
     * the owning loan because PaymentEntity intentionally stores no currency field.
     * Reversed originals are excluded from operational totals.
     */
    fun fromPayments(
        payments: List<PaymentEntity>,
        loanCurrencies: Map<String, String>,
        reversedOperationIds: Set<String>,
        zone: ZoneId = ZoneId.of("UTC")
    ): List<MonthlyPaymentTotal> {
        val effective = PaymentReporting.effectivePayments(payments, reversedOperationIds)
        val entries = effective.map { payment ->
            val currency = requireNotNull(loanCurrencies[payment.loanId]) {
                "No se encontró la moneda del préstamo " + payment.loanId
            }
            PaymentReportEntry(payment.operationId, payment.amountMinor, currency, payment.createdAt)
        }
        return aggregate(entries, zone)
    }
    private val supportedCurrencies = setOf("DOP", "USD", "EUR")

    fun aggregate(entries: List<PaymentReportEntry>, zone: ZoneId = ZoneId.of("UTC")): List<MonthlyPaymentTotal> {
        require(entries.map { it.operationId }.distinct().size == entries.size) { "Operaciones duplicadas" }
        entries.forEach {
            require(it.operationId.isNotBlank()) { "Identificador obligatorio" }
            require(it.amountMinor > 0L) { "El importe del pago debe ser positivo" }
            require(it.currency in supportedCurrencies) { "Moneda no soportada" }
        }
        return entries.groupBy { entry ->
            Pair(YearMonth.from(Instant.ofEpochMilli(entry.paidAtMillis).atZone(zone)), entry.currency)
        }.map { (key, rows) ->
            MonthlyPaymentTotal(key.first, key.second,
                rows.fold(0L) { total, row -> Math.addExact(total, row.amountMinor) }, rows.size)
        }.sortedWith(compareBy<MonthlyPaymentTotal> { it.month }.thenBy { it.currency })
    }
}
