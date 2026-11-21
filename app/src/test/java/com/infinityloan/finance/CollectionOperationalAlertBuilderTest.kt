package com.infinityloan.finance

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CollectionOperationalAlertBuilderTest {
    private val today = LocalDate.parse("2026-11-20")

    @Test fun buildsAndPrioritizesCriticalCollectionAlerts() {
        val risks = listOf(
            ClientCollectionRiskItem("c-critical", ClientCollectionRiskLevel.CRITICAL, 85, 3, 1, 1, 20, mapOf("DOP" to 500L)),
            ClientCollectionRiskItem("c-high", ClientCollectionRiskLevel.HIGH, 55, 1, 1, 0, 5, mapOf("USD" to 200L)),
            ClientCollectionRiskItem("c-low", ClientCollectionRiskLevel.LOW, 10, 0, 0, 0, 0, emptyMap())
        )
        val visits = listOf(
            CollectionVisitRecord("v-old", "l1", "c-visit", "collector-1", today.minusDays(3)),
            CollectionVisitRecord("v-today", "l2", "c-today", "collector-2", today),
            CollectionVisitRecord("v-future", "l3", "c-future", "collector-2", today.plusDays(1)),
            CollectionVisitRecord("v-closed", "l4", "c-closed", "collector-1", today.minusDays(5), CollectionVisitStatus.CANCELLED)
        )
        val promises = listOf(
            PromiseToPayRecord("p-old", "l5", "c-promise", "collector-1", 100, "DOP", today.minusDays(2)),
            PromiseToPayRecord("p-today", "l6", "c-promise-today", "collector-2", 100, "USD", today),
            PromiseToPayRecord("p-kept", "l7", "c-kept", "collector-1", 100, "DOP", today.minusDays(1), PromiseToPayStatus.KEPT)
        )

        val alerts = CollectionOperationalAlertBuilder.build(risks, visits, promises, today)

        assertEquals(6, alerts.size)
        assertEquals(CollectionAlertSeverity.CRITICAL, alerts.first().severity)
        assertEquals("risk-critical:c-critical", alerts.first().alertId)
        assertTrue(alerts.any { it.type == CollectionAlertType.VISIT_OVERDUE && it.daysOverdue == 3L })
        assertTrue(alerts.any { it.type == CollectionAlertType.PROMISE_OVERDUE && it.daysOverdue == 2L })
        assertTrue(alerts.any { it.type == CollectionAlertType.VISIT_DUE_TODAY })
        assertTrue(alerts.any { it.type == CollectionAlertType.PROMISE_DUE_TODAY })
        assertFalse(alerts.any { it.referenceId == "v-future" || it.referenceId == "p-kept" })
    }

    @Test fun rejectsDuplicateOperationalIdentifiers() {
        val duplicate = listOf(
            CollectionVisitRecord("same", "l1", "c1", "collector", today),
            CollectionVisitRecord("same", "l2", "c2", "collector", today)
        )
        try {
            CollectionOperationalAlertBuilder.build(emptyList(), duplicate, emptyList(), today)
            fail("Expected duplicate visit rejection")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun emptyInputsProduceNoAlerts() {
        assertTrue(CollectionOperationalAlertBuilder.build(emptyList(), emptyList(), emptyList(), today).isEmpty())
    }
}
