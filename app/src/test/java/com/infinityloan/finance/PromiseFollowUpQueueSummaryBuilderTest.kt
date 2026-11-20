package com.infinityloan.finance

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PromiseFollowUpQueueSummaryBuilderTest {
 private fun item(id:String, urgency:PromiseFollowUpUrgency=PromiseFollowUpUrgency.HIGH,currency:String="DOP",amount:Long=100,collector:String="C1") =
  PromiseFollowUpItem(id,"L$id","CL$id",collector,currency,amount,LocalDate.of(2026,11,1),10,urgency)
 @Test fun groupsUrgencyAndCurrencies() {
  val s=PromiseFollowUpQueueSummaryBuilder.build(listOf(item("1"),item("2",currency="USD",collector="C2"),item("3",PromiseFollowUpUrgency.CRITICAL)))
  assertEquals(3,s.total);assertEquals(2,s.urgency[PromiseFollowUpUrgency.HIGH]?.count);assertEquals(200L,s.urgency[PromiseFollowUpUrgency.HIGH]?.amountMinorByCurrency?.get("DOP"));assertEquals(2,s.collectorCount)
 }
 @Test fun emptyQueue() { assertEquals(0,PromiseFollowUpQueueSummaryBuilder.build(emptyList()).total) }
 @Test fun rejectsDuplicatesAndInvalidCurrency() {
  val x=item("x");assertThrows(IllegalArgumentException::class.java){PromiseFollowUpQueueSummaryBuilder.build(listOf(x,x))}
  assertThrows(IllegalArgumentException::class.java){PromiseFollowUpQueueSummaryBuilder.build(listOf(item("bad",currency="JPY")))}
 }
 @Test fun detectsOverflowWithinCurrency() {
  assertThrows(ArithmeticException::class.java){PromiseFollowUpQueueSummaryBuilder.build(listOf(item("a",amount=Long.MAX_VALUE),item("b",amount=1)))}
 }
}
