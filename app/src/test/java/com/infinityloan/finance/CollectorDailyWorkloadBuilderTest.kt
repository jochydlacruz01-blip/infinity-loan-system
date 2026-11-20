package com.infinityloan.finance

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CollectorDailyWorkloadBuilderTest {
    private val day = LocalDate.of(2026, 11, 21)
    private fun visit(id: String, collector: String = "C1", status: CollectionVisitStatus = CollectionVisitStatus.SCHEDULED, client: String = id) =
        CollectionVisitRecord(visitId=id, loanId="L$id", clientId=client, collectorId=collector, plannedDate=day, status=status)

    @Test fun calculatesCountsAndIgnoresCancelledForCapacity() {
        val rows=CollectorDailyWorkloadBuilder.build(listOf(visit("1"),visit("2",status=CollectionVisitStatus.PROMISE_TO_PAY),visit("3",status=CollectionVisitStatus.PAID),visit("4",status=CollectionVisitStatus.CANCELLED)),day,3).single()
        assertEquals(3,rows.total); assertEquals(1,rows.pending); assertEquals(1,rows.inProgress); assertEquals(1,rows.completed); assertEquals(1,rows.cancelled); assertFalse(rows.overCapacity)
    }
    @Test fun flagsCapacityAndSortsByWorkload() {
        val rows=CollectorDailyWorkloadBuilder.build(listOf(visit("a","Z"),visit("b","Z"),visit("c","Z"),visit("x","A")),day,2)
        assertEquals("Z",rows.first().collectorId);assertTrue(rows.first().overCapacity);assertEquals("A",rows.last().collectorId)
    }
    @Test fun excludesOtherDatesAndCancelledIds() {
        val other=visit("other").copy(plannedDate=day.plusDays(1))
        val rows=CollectorDailyWorkloadBuilder.build(listOf(other,visit("cancel",status=CollectionVisitStatus.CANCELLED)),day)
        assertTrue(rows.isEmpty())
    }
    @Test fun duplicateIdsAndInvalidCapacityAreRejected() {
        assertThrows(IllegalArgumentException::class.java){CollectorDailyWorkloadBuilder.build(listOf(visit("x"),visit("x")),day)}
        assertThrows(IllegalArgumentException::class.java){CollectorDailyWorkloadBuilder.build(emptyList(),day,0)}
    }
}
