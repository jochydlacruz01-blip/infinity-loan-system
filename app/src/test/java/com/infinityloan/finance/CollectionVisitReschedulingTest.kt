package com.infinityloan.finance

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CollectionVisitReschedulingTest {
 @Test fun reschedulesWithoutChangingIdentityAndTrimsNote() {
  val old=LocalDate.of(2026,11,20);val next=old.plusDays(2)
  val v=CollectionVisitRecord(visitId="v",loanId="l",clientId="c",collectorId="a",plannedDate=old)
  val moved=CollectionVisitRescheduling.reschedule(v,next,"  cambio  ")
  assertEquals("v",moved.visitId);assertEquals(next,moved.plannedDate);assertEquals("cambio",moved.outcomeNote)
 }
 @Test fun rejectsClosedVisits() {
  val v=CollectionVisitRecord(visitId="v",loanId="l",clientId="c",collectorId="a",plannedDate=LocalDate.now(),status=CollectionVisitStatus.PAID)
  try { CollectionVisitRescheduling.reschedule(v,LocalDate.now().plusDays(1));fail("expected") } catch(_:IllegalArgumentException){}
 }
 @Test fun reportsCollectorDateCollisionsAndIgnoresClosed() {
  val d=LocalDate.of(2026,11,20)
  val a=CollectionVisitRecord(visitId="a",loanId="l1",clientId="c1",collectorId="x",plannedDate=d)
  val b=CollectionVisitRecord(visitId="b",loanId="l2",clientId="c2",collectorId="x",plannedDate=d)
  val paid=CollectionVisitRecord(visitId="p",loanId="l3",clientId="c3",collectorId="x",plannedDate=d,status=CollectionVisitStatus.PAID)
  assertEquals(listOf("x" to d),CollectionVisitRescheduling.conflicts(listOf(a,b,paid)))
 }
 @Test fun emptyConflicts() { assertTrue(CollectionVisitRescheduling.conflicts(emptyList()).isEmpty()) }
}
