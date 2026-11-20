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


    @Test fun transitionMatrixRejectsEveryUnlistedStateChange() {
        val allowed = mapOf(
            CollectionVisitStatus.SCHEDULED to setOf(CollectionVisitStatus.ATTEMPTED, CollectionVisitStatus.CONTACTED, CollectionVisitStatus.CANCELLED, CollectionVisitStatus.UNREACHABLE),
            CollectionVisitStatus.ATTEMPTED to setOf(CollectionVisitStatus.CONTACTED, CollectionVisitStatus.PROMISE_TO_PAY, CollectionVisitStatus.PAID, CollectionVisitStatus.UNREACHABLE, CollectionVisitStatus.CANCELLED),
            CollectionVisitStatus.CONTACTED to setOf(CollectionVisitStatus.PROMISE_TO_PAY, CollectionVisitStatus.PAID, CollectionVisitStatus.UNREACHABLE, CollectionVisitStatus.CANCELLED),
            CollectionVisitStatus.PROMISE_TO_PAY to setOf(CollectionVisitStatus.PAID, CollectionVisitStatus.ATTEMPTED, CollectionVisitStatus.UNREACHABLE, CollectionVisitStatus.CANCELLED),
            CollectionVisitStatus.UNREACHABLE to setOf(CollectionVisitStatus.ATTEMPTED, CollectionVisitStatus.CONTACTED, CollectionVisitStatus.CANCELLED),
            CollectionVisitStatus.PAID to emptySet(),
            CollectionVisitStatus.CANCELLED to emptySet()
        )
        for (current in CollectionVisitStatus.values()) {
            for (next in CollectionVisitStatus.values()) {
                val shouldAllow = next in (allowed[current] ?: emptySet())
                val accepted = try {
                    CollectionVisitWorkflow.transition(visit(current), next)
                    true
                } catch (_: IllegalArgumentException) {
                    false
                }
                assertEquals("Unexpected transition $current -> $next", shouldAllow, accepted)
            }
        }
    }

    @Test fun transitionTrimsOutcomeNoteAndKeepsRecordIdentity() {
        val original = visit()
        val updated = CollectionVisitWorkflow.transition(original, CollectionVisitStatus.ATTEMPTED, "  Cliente no estaba  ")
        assertEquals("Cliente no estaba", updated.outcomeNote)
        assertEquals(original.visitId, updated.visitId)
        assertEquals(original.loanId, updated.loanId)
        assertEquals(original.collectorId, updated.collectorId)
        assertEquals(original.plannedDate, updated.plannedDate)
    }

    @Test fun rejectsBlankBusinessIdentifiersAndOversizedInitialNote() {
        for (invalid in listOf(
            visit().copy(visitId = " "),
            visit().copy(loanId = ""),
            visit().copy(clientId = " "),
            visit().copy(collectorId = " ")
        )) {
            try {
                CollectionVisitWorkflow.validate(invalid)
                fail("Expected invalid record rejection")
            } catch (_: IllegalArgumentException) {}
        }
        try {
            CollectionVisitWorkflow.validate(visit().copy(outcomeNote = "x".repeat(2001)))
            fail("Expected oversized note rejection")
        } catch (_: IllegalArgumentException) {}
    }

}
