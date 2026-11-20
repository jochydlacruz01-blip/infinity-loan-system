package com.infinityloan.finance

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CollectionVisitWorkflowTest {
    private fun visit(status: CollectionVisitStatus = CollectionVisitStatus.SCHEDULED) = CollectionVisitRecord(
        visitId = "v1", loanId = "l1", clientId = "c1", collectorId = "staff1",
        plannedDate = LocalDate.parse("2026-11-20"), status = status
    )

    @Test fun validProgressionAndOutcomeNote() {
        val attempted = CollectionVisitWorkflow.transition(visit(), CollectionVisitStatus.ATTEMPTED, "Visita realizada")
        val promise = CollectionVisitWorkflow.transition(attempted, CollectionVisitStatus.PROMISE_TO_PAY, "Pagará mañana")
        assertEquals(CollectionVisitStatus.PROMISE_TO_PAY, promise.status)
        assertEquals("Pagará mañana", promise.outcomeNote)
    }

    @Test fun terminalStatesCannotBeChanged() {
        for (status in listOf(CollectionVisitStatus.PAID, CollectionVisitStatus.CANCELLED)) {
            try { CollectionVisitWorkflow.transition(visit(status), CollectionVisitStatus.ATTEMPTED); fail("Expected rejection") }
            catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun requiresAssignedCollectorAndBoundedNote() {
        try { CollectionVisitWorkflow.validate(visit().copy(collectorId = "")); fail("Expected rejection") }
        catch (_: IllegalArgumentException) {}
        try { CollectionVisitWorkflow.transition(visit(), CollectionVisitStatus.ATTEMPTED, "x".repeat(2001)); fail("Expected rejection") }
        catch (_: IllegalArgumentException) {}
    }
}
