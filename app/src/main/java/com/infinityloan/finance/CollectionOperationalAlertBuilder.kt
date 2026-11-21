package com.infinityloan.finance

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CollectionAlertSeverity { CRITICAL, HIGH, MEDIUM }
enum class CollectionAlertType {
    CLIENT_CRITICAL, CLIENT_HIGH, VISIT_OVERDUE, VISIT_DUE_TODAY, PROMISE_OVERDUE, PROMISE_DUE_TODAY
}

data class CollectionOperationalAlert(
    val alertId: String,
    val type: CollectionAlertType,
    val severity: CollectionAlertSeverity,
    val clientId: String,
    val assignedTo: String?,
    val referenceId: String,
    val dueDate: LocalDate?,
    val daysOverdue: Long
)

/**
 * Builds actionable collection alerts from already validated operational records.
 * Alerts contain identifiers only: they do not expose customer contact data or infer money recovered.
 */
object CollectionOperationalAlertBuilder {
    fun build(
        risks: Iterable<ClientCollectionRiskItem>,
        visits: Iterable<CollectionVisitRecord>,
        promises: Iterable<PromiseToPayRecord>,
        asOf: LocalDate
    ): List<CollectionOperationalAlert> {
        val riskRows = risks.toList()
        require(riskRows.map { it.clientId }.distinct().size == riskRows.size) { "Riesgo duplicado por cliente" }
        riskRows.forEach {
            require(it.clientId.isNotBlank() && it.riskScore in 0..100)
        }

        val visitRows = visits.toList()
        visitRows.forEach(CollectionVisitWorkflow::validate)
        require(visitRows.map { it.visitId }.distinct().size == visitRows.size) { "Visitas duplicadas" }

        val promiseRows = promises.toList()
        promiseRows.forEach(PromiseToPayWorkflow::validate)
        require(promiseRows.map { it.promiseId }.distinct().size == promiseRows.size) { "Promesas duplicadas" }

        val alerts = mutableListOf<CollectionOperationalAlert>()
        riskRows.filter { it.riskLevel == ClientCollectionRiskLevel.CRITICAL || it.riskLevel == ClientCollectionRiskLevel.HIGH }
            .forEach { risk ->
                val critical = risk.riskLevel == ClientCollectionRiskLevel.CRITICAL
                alerts += CollectionOperationalAlert(
                    alertId = "${if (critical) "risk-critical" else "risk-high"}:${risk.clientId}",
                    type = if (critical) CollectionAlertType.CLIENT_CRITICAL else CollectionAlertType.CLIENT_HIGH,
                    severity = if (critical) CollectionAlertSeverity.CRITICAL else CollectionAlertSeverity.HIGH,
                    clientId = risk.clientId,
                    assignedTo = null,
                    referenceId = risk.clientId,
                    dueDate = null,
                    daysOverdue = risk.oldestOverdueDays.coerceAtLeast(0)
                )
            }

        visitRows.filter {
            it.status in setOf(CollectionVisitStatus.SCHEDULED, CollectionVisitStatus.ATTEMPTED,
                CollectionVisitStatus.CONTACTED, CollectionVisitStatus.PROMISE_TO_PAY, CollectionVisitStatus.UNREACHABLE) &&
                !it.plannedDate.isAfter(asOf)
        }.forEach { visit ->
            val overdue = ChronoUnit.DAYS.between(visit.plannedDate, asOf)
            alerts += CollectionOperationalAlert(
                "visit:${visit.visitId}",
                if (overdue > 0) CollectionAlertType.VISIT_OVERDUE else CollectionAlertType.VISIT_DUE_TODAY,
                if (overdue > 0) CollectionAlertSeverity.HIGH else CollectionAlertSeverity.MEDIUM,
                visit.clientId, visit.collectorId, visit.visitId, visit.plannedDate, overdue
            )
        }

        promiseRows.filter { it.status == PromiseToPayStatus.OPEN && !it.promisedDate.isAfter(asOf) }
            .forEach { promise ->
                val overdue = ChronoUnit.DAYS.between(promise.promisedDate, asOf)
                alerts += CollectionOperationalAlert(
                    "promise:${promise.promiseId}",
                    if (overdue > 0) CollectionAlertType.PROMISE_OVERDUE else CollectionAlertType.PROMISE_DUE_TODAY,
                    if (overdue > 0) CollectionAlertSeverity.CRITICAL else CollectionAlertSeverity.MEDIUM,
                    promise.clientId, promise.collectorId, promise.promiseId, promise.promisedDate, overdue
                )
            }

        return alerts.sortedWith(
            compareBy<CollectionOperationalAlert> { it.severity.ordinal }
                .thenByDescending { it.daysOverdue }
                .thenBy { it.dueDate ?: LocalDate.MAX }
                .thenBy { it.alertId }
        )
    }
}
