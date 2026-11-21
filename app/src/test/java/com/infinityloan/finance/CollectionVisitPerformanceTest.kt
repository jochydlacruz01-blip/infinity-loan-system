package com.infinityloan.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class CollectionVisitPerformanceTest {
    private val day = LocalDate.parse("2026-11-20")
    private fun row(id: String, collector: String, status: CollectionVisitStatus, date: LocalDate = day) =
        CollectionVisitRecord(visitId=id, loanId="loan$id", clientId="client$id", collectorId=collector, plannedDate=date, status=status)

    @Test fun groupsCountsAndRanksCollectorsDeterministically() {
        val rows = listOf(
            row("1","b",CollectionVisitStatus.PAID),
            row("2","b",CollectionVisitStatus.PROMISE_TO_PAY),
            row("3","b",CollectionVisitStatus.CANCELLED),
            row("4","a",CollectionVisitStatus.PAID),
            row("5","a",CollectionVisitStatus.PAID),
            row("6","a",CollectionVisitStatus.UNREACHABLE),
            row("7","outside",CollectionVisitStatus.PAID,day.plusDays(1))
        )
        val result = CollectionVisitPerformance.summarize(rows,day,day)
        assertEquals(listOf("a","b"),result.map{it.collectorId})
        val a=result[0]
        assertEquals(3,a.planned); assertEquals(2,a.paid); assertEquals(1,a.unreachable)
        assertEquals(100,a.completionRatePercent)
        assertEquals(66,a.paidRatePercent)
        val b=result[1]
        assertEquals(2,b.planned); assertEquals(1,b.paid); assertEquals(1,b.promises); assertEquals(1,b.cancelled)
        assertEquals(100,b.completionRatePercent)
        assertEquals(50,b.paidRatePercent)
    }

    @Test fun emptyRangeReturnsEmptyAndInvertedRangeRejected() {
        assertEquals(emptyList<CollectorVisitPerformance>(),CollectionVisitPerformance.summarize(emptyList(),day,day))
        assertThrows(IllegalArgumentException::class.java) { CollectionVisitPerformance.summarize(emptyList(),day,day.minusDays(1)) }
    }

    @Test fun scheduledVisitsRemainPlannedButNotCompleted() {
        val result = CollectionVisitPerformance.summarize(
            listOf(
                row("scheduled", "c", CollectionVisitStatus.SCHEDULED),
                row("attempted", "c", CollectionVisitStatus.ATTEMPTED)
            ),
            day,
            day
        ).single()
        assertEquals(2, result.planned)
        assertEquals(1, result.completed)
        assertEquals(50, result.completionRatePercent)
        assertEquals(0, result.paidRatePercent)
    }

    @Test fun zeroPlannedVisitsDoesNotDivideByZero() {
        val result=CollectionVisitPerformance.summarize(listOf(row("x","c",CollectionVisitStatus.CANCELLED)),day,day)
        assertEquals(0,result.single().planned)
        assertEquals(0,result.single().completed)
        assertEquals(0,result.single().completionRatePercent)
        assertEquals(0,result.single().paidRatePercent)
        assertEquals(0,result.single().promiseRatePercent)
    }
}
