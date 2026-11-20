package com.infinityloan.finance

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class CollectionContactAttemptLogTest {
    private fun attempt(id:String="a1", collector:String="staff1", visit:String="v1", outcome:CollectionContactOutcome=CollectionContactOutcome.CONTACTED) =
        CollectionContactAttempt(id, visit, "loan1", "client1", collector, Instant.parse("2026-11-20T12:00:00Z"), CollectionContactChannel.PHONE, outcome, "Registro")

    @Test fun aggregatesByCollectorAndOutcome() {
        val rows=listOf(attempt(),attempt("a2", outcome=CollectionContactOutcome.NO_ANSWER),attempt("a3", collector="staff2", outcome=CollectionContactOutcome.PROMISE_TO_PAY),attempt("a4", outcome=CollectionContactOutcome.FOLLOW_UP_REQUIRED))
        val summaries=CollectionContactAttemptLog.summarize(rows)
        assertEquals(2,summaries[0].attemptCount)
        assertEquals(1,summaries[0].successfulContacts)
        assertEquals(1,summaries[0].noAnswerCount)
        assertEquals(1,summaries[0].followUps)
        assertEquals("staff1",summaries[0].collectorId)
        assertEquals(1,summaries[1].promises)
    }

    @Test fun historyFilteredAndChronological() {
        val earlier=attempt("a2").copy(occurredAt=Instant.parse("2026-11-19T10:00:00Z"))
        val later=attempt("a1")
        val history=CollectionContactAttemptLog.historyForVisit(listOf(later,attempt("a3",visit="v2"),earlier),"v1")
        assertEquals(listOf("a2","a1"),history.map { it.attemptId })
    }

    @Test fun rejectsDuplicatesAndInvalidFields() {
        try { CollectionContactAttemptLog.summarize(listOf(attempt(),attempt())); fail("Expected duplicate rejection") } catch (_:IllegalArgumentException){}
        try { CollectionContactAttemptLog.validate(attempt().copy(note="x".repeat(2001))); fail("Expected note rejection") } catch (_:IllegalArgumentException){}
        try { CollectionContactAttemptLog.validate(attempt().copy(collectorId=" ")); fail("Expected collector rejection") } catch (_:IllegalArgumentException){}
    }

    @Test fun emptyResultsAreEmpty() {
        assertTrue(CollectionContactAttemptLog.summarize(emptyList()).isEmpty())
        assertTrue(CollectionContactAttemptLog.historyForVisit(emptyList(),"v1").isEmpty())
    }
}
