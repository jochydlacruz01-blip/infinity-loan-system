package com.infinityloan.finance

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CollectionVisitPlannerTest {
 private val date=LocalDate.parse("2026-11-20")
 private fun record(id:String, collector:String="c1", status:CollectionVisitStatus=CollectionVisitStatus.SCHEDULED, day:LocalDate=date)=CollectionVisitRecord(visitId=id,loanId="l$id",clientId=id,collectorId=collector,plannedDate=day,status=status)
 @Test fun dayExcludesCancelledAndOrdersScheduledFirst(){
   val rows=CollectionVisitPlanner.forDay(listOf(record("b",status=CollectionVisitStatus.ATTEMPTED),record("a"),record("x",status=CollectionVisitStatus.CANCELLED)),date)
   assertEquals(listOf("a","b"),rows.map{it.visitId})
 }
 @Test fun dayOrderingIsStableForCollectorAndClientCase(){
   val rows=CollectionVisitPlanner.forDay(listOf(record("z",collector="B"),record("b",collector="a"),record("a",collector="A"),record("c",collector="b")),date)
   assertEquals(listOf("a","b","c","z"),rows.map{it.visitId})
 }
 @Test fun collectorDateRange(){
   val rows=CollectionVisitPlanner.forCollector(listOf(record("1"),record("2",collector="c2"),record("3",day=date.plusDays(1))),"c1",date,date)
   assertEquals(listOf("1"),rows.map{it.visitId})
 }
 @Test fun rejectsBlankCollectorAndInvertedRange(){
   assertThrows(IllegalArgumentException::class.java) { CollectionVisitPlanner.forCollector(emptyList()," ",date,date) }
   assertThrows(IllegalArgumentException::class.java) { CollectionVisitPlanner.forCollector(emptyList(),"c1",date,date.minusDays(1)) }
 }
 @Test fun daySummaryCountsStatusesAndCancelledSeparately(){
   val rows=listOf(record("1"),record("2",status=CollectionVisitStatus.PROMISE_TO_PAY),record("3",status=CollectionVisitStatus.PAID),record("4",status=CollectionVisitStatus.UNREACHABLE),record("5",status=CollectionVisitStatus.CANCELLED),record("6",day=date.plusDays(1)))
   val summary=CollectionVisitPlanner.summarize(rows,date)
   assertEquals(1,summary.scheduled);assertEquals(1,summary.attempted);assertEquals(1,summary.promises);assertEquals(1,summary.completed);assertEquals(1,summary.cancelled)
 }
}
