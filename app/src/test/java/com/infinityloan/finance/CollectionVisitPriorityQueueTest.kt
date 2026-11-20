package com.infinityloan.finance

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CollectionVisitPriorityQueueTest {
 private val today=LocalDate.of(2026,11,20)
 private fun row(id:String,date:LocalDate,status:CollectionVisitStatus=CollectionVisitStatus.SCHEDULED)=CollectionVisitRecord(id,"L$id","C$id","collector",date,status)
 @Test fun overdueThenTodayThenUpcomingAndClosedExcluded() {
  val rows=listOf(row("future",today.plusDays(2)),row("today",today),row("late2",today.minusDays(2)),row("late5",today.minusDays(5)),row("paid",today,CollectionVisitStatus.PAID),row("cancel",today,CollectionVisitStatus.CANCELLED))
  val result=CollectionVisitPriorityQueue.build(rows,today)
  assertEquals(listOf("late5","late2","today","future"),result.map{it.record.visitId})
  assertEquals(5L,result.first().daysLate)
 }
 @Test fun rejectsDuplicateIds() {
  val r=row("same",today)
  assertThrows(IllegalArgumentException::class.java){CollectionVisitPriorityQueue.build(listOf(r,r),today)}
 }
 @Test fun overdueStatusRetainsQueueAndReportsAge() {
  val r=row("attempted",today.minusDays(1),CollectionVisitStatus.ATTEMPTED)
  assertEquals(CollectionVisitPriority.OVERDUE,CollectionVisitPriorityQueue.build(listOf(r),today).single().priority)
 }
}
