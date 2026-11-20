package com.infinityloan.finance

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CollectorOperationsSnapshotBuilderTest {
    private val date = LocalDate.of(2026, 11, 20)

    @Test fun combinesVisitAndPromisePressureAndRanksDeterministically() {
        val visits = listOf(
            CollectionVisitRecord(visitId="v1",loanId="l1",clientId="c1",collectorId="b",plannedDate=date),
            CollectionVisitRecord(visitId="v2",loanId="l2",clientId="c2",collectorId="a",plannedDate=date,status=CollectionVisitStatus.PAID),
            CollectionVisitRecord(visitId="v3",loanId="l3",clientId="c3",collectorId="a",plannedDate=date,status=CollectionVisitStatus.CONTACTED)
        )
        val promises = listOf(
            PromiseFollowUpItem("p1","l4","c4","a","DOP",1000,date.minusDays(35),35,PromiseFollowUpUrgency.CRITICAL),
            PromiseFollowUpItem("p2","l5","c5","b","USD",2000,date.minusDays(10),10,PromiseFollowUpUrgency.MEDIUM)
        )
        val result = CollectorOperationsSnapshotBuilder.build(visits,promises,date)
        assertEquals("a",result.first().collectorId)
        assertEquals(1,result.first().completedVisits)
        assertEquals(1,result.first().activeVisits)
        assertEquals(1,result.first().criticalPromises)
        assertEquals(1000L,result.first().overdueAmountMinorByCurrency["DOP"])
        assertEquals(2000L,result.last().overdueAmountMinorByCurrency["USD"])
    }

    @Test fun emptyInputProducesEmptySnapshot() {
        assertTrue(CollectorOperationsSnapshotBuilder.build(emptyList(),emptyList(),date).isEmpty())
    }

    @Test fun rejectsDuplicateIds() {
        val v=CollectionVisitRecord(visitId="dup",loanId="l",clientId="c",collectorId="a",plannedDate=date)
        try { CollectorOperationsSnapshotBuilder.build(listOf(v,v),emptyList(),date); fail("expected validation") }
        catch(_:IllegalArgumentException) {}
    }

    @Test fun rejectsOverflow() {
        val p1=PromiseFollowUpItem("p1","l","c","a","DOP",Long.MAX_VALUE,date.minusDays(8),8,PromiseFollowUpUrgency.MEDIUM)
        val p2=PromiseFollowUpItem("p2","l","c","b","DOP",1,date.minusDays(9),9,PromiseFollowUpUrgency.MEDIUM)
        try { CollectorOperationsSnapshotBuilder.build(emptyList(),listOf(p1,p2),date); fail("expected overflow") }
        catch(_:ArithmeticException) {}
    }
}
