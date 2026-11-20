package com.infinityloan.finance

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SupervisorCollectionDashboardBuilderTest {
    private val today = LocalDate.parse("2026-11-20")
    private fun visit(id: String, collector: String, days: Long, status: CollectionVisitStatus) =
        CollectionVisitRecord(id, "loan-$id", "client-$id", collector, today.plusDays(days), status)
    private fun promise(id: String, days: Long, status: PromiseToPayStatus = PromiseToPayStatus.OPEN) =
        PromiseToPayRecord(id, "loan-$id", "client-$id", "collector-1", 100, "DOP", today.plusDays(days), status)
    private fun risk(client: String, level: ClientCollectionRiskLevel, score: Int, installments: Int) =
        ClientCollectionRiskItem(client, level, score, installments, 1, 0, 10, mapOf("DOP" to 100L))

    @Test fun aggregatesSupervisorAndCollectorWorkloadWithoutMixingInferredRecovery() {
        val result = SupervisorCollectionDashboardBuilder.build(
            listOf(risk("c1", ClientCollectionRiskLevel.CRITICAL, 80, 2), risk("c2", ClientCollectionRiskLevel.HIGH, 50, 1)),
            listOf(
                visit("v1", "collector-b", -1, CollectionVisitStatus.SCHEDULED),
                visit("v2", "collector-a", 0, CollectionVisitStatus.PAID),
                visit("v3", "collector-a", 2, CollectionVisitStatus.UNREACHABLE),
                visit("v4", "collector-b", -2, CollectionVisitStatus.CANCELLED)
            ),
            listOf(promise("p1", -1), promise("p2", 0), promise("p3", -3, PromiseToPayStatus.KEPT)),
            today
        )
        assertEquals(2, result.totalClientsAtRisk)
        assertEquals(1, result.criticalClients)
        assertEquals(1, result.highRiskClients)
        assertEquals(3, result.overdueInstallments)
        assertEquals(2, result.pendingVisits)
        assertEquals(1, result.visitsDueOrOverdue)
        assertEquals(2, result.completedVisits)
        assertEquals(1, result.paidVisits)
        assertEquals(1, result.promisesOverdue)
        assertEquals(listOf("collector-a", "collector-b"), result.collectors.map { it.collectorId })
        assertEquals(2, result.collectors.first().assignedVisits)
        assertEquals(1, result.collectors.first().pendingVisits)
        assertEquals(1, result.collectors.first().paidVisits)
    }

    @Test fun emptyInputsReturnZeroAndDuplicateRecordsAreRejected() {
        val empty = SupervisorCollectionDashboardBuilder.build(emptyList(), emptyList(), emptyList(), today)
        assertEquals(0, empty.totalClientsAtRisk)
        assertTrue(empty.collectors.isEmpty())
        try {
            SupervisorCollectionDashboardBuilder.build(emptyList(), listOf(visit("x", "collector", 0, CollectionVisitStatus.SCHEDULED), visit("x", "collector", 1, CollectionVisitStatus.PAID)), emptyList(), today)
            fail("Expected duplicate visit validation")
        } catch (_: IllegalArgumentException) {}
    }
}
