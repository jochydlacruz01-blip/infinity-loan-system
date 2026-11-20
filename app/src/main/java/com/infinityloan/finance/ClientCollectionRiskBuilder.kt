package com.infinityloan.finance

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ClientCollectionRiskLevel { CRITICAL, HIGH, MEDIUM, LOW }

data class ClientCollectionRiskInput(
    val loanId: String,
    val clientId: String,
    val currency: String,
    val dueDate: LocalDate,
    val unpaidMinor: Long
)

data class ClientCollectionRiskItem(
    val clientId: String,
    val riskLevel: ClientCollectionRiskLevel,
    val riskScore: Int,
    val overdueInstallments: Int,
    val overdueLoans: Int,
    val missedPromises: Int,
    val oldestOverdueDays: Long,
    val overdueTotalsMinorByCurrency: Map<String, Long>
)

/**
 * Explainable operational risk indicator. It does not predict default or replace
 * credit underwriting; it ranks current collection workload using overdue exposure
 * and broken open promises. Monetary balances are never mixed across currencies.
 */
object ClientCollectionRiskBuilder {
    private val currencies = setOf("DOP", "USD", "EUR")

    fun build(
        installments: Iterable<ClientCollectionRiskInput>,
        promises: Iterable<PromiseToPayRecord>,
        asOf: LocalDate
    ): List<ClientCollectionRiskItem> {
        val rows = installments.toList()
        rows.forEach {
            require(it.loanId.isNotBlank() && it.clientId.isNotBlank()) { "Préstamo y cliente obligatorios" }
            require(it.currency in currencies) { "Moneda no admitida: ${it.currency}" }
            require(it.unpaidMinor >= 0) { "Saldo pendiente inválido" }
        }
        val missed = PromiseFollowUpQueue.build(promises, asOf).groupBy { it.clientId }
        val overdue = rows.filter { it.unpaidMinor > 0 && it.dueDate.isBefore(asOf) }
        val grouped = (overdue.map { it.clientId } + missed.keys).distinct().sorted()
        return grouped.map { client ->
            val clientRows = overdue.filter { it.clientId == client }
            val clientMissed = missed[client].orEmpty()
            val oldest = clientRows.maxOfOrNull { ChronoUnit.DAYS.between(it.dueDate, asOf) } ?: 0L
            val loans = clientRows.map { it.loanId }.distinct().size
            val score = (minOf(oldest, 60L).toInt() + minOf(clientRows.size * 5, 20) +
                minOf(loans * 10, 20) + minOf(clientMissed.size * 15, 30)).coerceIn(0, 100)
            val level = when {
                score >= 70 -> ClientCollectionRiskLevel.CRITICAL
                score >= 45 -> ClientCollectionRiskLevel.HIGH
                score >= 20 -> ClientCollectionRiskLevel.MEDIUM
                else -> ClientCollectionRiskLevel.LOW
            }
            ClientCollectionRiskItem(
                client, level, score, clientRows.size, loans, clientMissed.size, oldest,
                clientRows.groupBy { it.currency }.mapValues { (_, values) -> values.sumOf { it.unpaidMinor } }.toSortedMap()
            )
        }.sortedWith(compareByDescending<ClientCollectionRiskItem> { it.riskScore }.thenBy { it.clientId })
    }
}
