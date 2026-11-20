package com.infinityloan.finance

import java.time.LocalDate

data class CollectorCollectionSnapshot(
    val collectorId: String,
    val assignedVisits: Int,
    val pendingVisits: Int,
    val completedVisits: Int,
    val paidVisits: Int,
    val unreachableVisits: Int
)

data class SupervisorCollectionDashboard(
    val totalClientsAtRisk: Int,
    val criticalClients: Int,
    val highRiskClients: Int,
    val overdueInstallments: Int,
    val pendingVisits: Int,
    val visitsDueOrOverdue: Int,
    val completedVisits: Int,
    val paidVisits: Int,
    val promisesOverdue: Int,
    val collectors: List<CollectorCollectionSnapshot>
)

/** Aggregates supplied operational records; it never infers payments or monetary recovery. */
object SupervisorCollectionDashboardBuilder {
    fun build(
        risks: Iterable<ClientCollectionRiskItem>,
        visits: Iterable<CollectionVisitRecord>,
        promises: Iterable<PromiseToPayRecord>,
        asOf: LocalDate
    ): SupervisorCollectionDashboard {
        val riskRows = risks.toList()
        require(riskRows.map { it.clientId }.distinct().size == riskRows.size) { "Riesgo duplicado por cliente" }
        riskRows.forEach {
            require(it.clientId.isNotBlank() && it.riskScore in 0..100)
            require(it.overdueInstallments >= 0 && it.overdueLoans >= 0 && it.missedPromises >= 0)
        }

        val visitRows = visits.toList()
        visitRows.forEach(CollectionVisitWorkflow::validate)
        require(visitRows.map { it.visitId }.distinct().size == visitRows.size) { "Visitas duplicadas" }
        val promiseRows = promises.toList()
        promiseRows.forEach(PromiseToPayWorkflow::validate)
        require(promiseRows.map { it.promiseId }.distinct().size == promiseRows.size) { "Promesas duplicadas" }

        val pendingStatuses = setOf(
            CollectionVisitStatus.SCHEDULED, CollectionVisitStatus.ATTEMPTED,
            CollectionVisitStatus.CONTACTED, CollectionVisitStatus.PROMISE_TO_PAY,
            CollectionVisitStatus.UNREACHABLE
        )
        val completedStatuses = setOf(CollectionVisitStatus.PAID, CollectionVisitStatus.CANCELLED)
        val collectorSnapshots = visitRows.groupBy { it.collectorId }.map { (collector, rows) ->
            CollectorCollectionSnapshot(
                collector,
                rows.size,
                rows.count { it.status in pendingStatuses },
                rows.count { it.status in completedStatuses },
                rows.count { it.status == CollectionVisitStatus.PAID },
                rows.count { it.status == CollectionVisitStatus.UNREACHABLE }
            )
        }.sortedBy { it.collectorId }

        val openOverduePromises = promiseRows.count {
            it.status == PromiseToPayStatus.OPEN && it.promisedDate.isBefore(asOf)
        }
        return SupervisorCollectionDashboard(
            totalClientsAtRisk = riskRows.size,
            criticalClients = riskRows.count { it.riskLevel == ClientCollectionRiskLevel.CRITICAL },
            highRiskClients = riskRows.count { it.riskLevel == ClientCollectionRiskLevel.HIGH },
            overdueInstallments = riskRows.sumOf { it.overdueInstallments },
            pendingVisits = visitRows.count { it.status in pendingStatuses },
            visitsDueOrOverdue = visitRows.count { it.status in pendingStatuses && !it.plannedDate.isAfter(asOf) },
            completedVisits = visitRows.count { it.status in completedStatuses },
            paidVisits = visitRows.count { it.status == CollectionVisitStatus.PAID },
            promisesOverdue = openOverduePromises,
            collectors = collectorSnapshots
        )
    }
}
