package com.infinityloan.finance

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CollectionPriorityDashboardBuilderTest {
 private fun item(id:String,c:String,p:CollectionVisitPriority,late:Long=0)=PrioritizedCollectionVisit(CollectionVisitRecord(id,"L$id","C$id",c,LocalDate.of(2026,11,20)),p,late)
 @Test fun totalsAndCollectorRanking() {
  val d=CollectionPriorityDashboardBuilder.build(listOf(item("1","A",CollectionVisitPriority.OVERDUE,4),item("2","A",CollectionVisitPriority.DUE_TODAY),item("3","B",CollectionVisitPriority.OVERDUE,2),item("4","B",CollectionVisitPriority.UPCOMING)))
  assertEquals(2,d.overdueTotal);assertEquals(1,d.dueTodayTotal);assertEquals(1,d.upcomingTotal);assertEquals(2,d.collectorCount)
  assertEquals("A",d.collectors.first().collectorId);assertEquals(4L,d.collectors.first().overdueDaysMax)
 }
 @Test fun emptyAndDuplicateValidation() {
  assertEquals(0,CollectionPriorityDashboardBuilder.build(emptyList()).collectorCount)
  val x=item("x","A",CollectionVisitPriority.DUE_TODAY)
  assertThrows(IllegalArgumentException::class.java){CollectionPriorityDashboardBuilder.build(listOf(x,x))}
 }
}
