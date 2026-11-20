package com.infinityloan.finance

import org.junit.Assert.*
import org.junit.Test

class PromiseFollowUpCollectorSummaryTest {
 private fun item(id:String,collector:String,urgency:PromiseFollowUpUrgency,currency:String,amount:Long)=
  PromiseFollowUpItem(id,"loan$id","client$id",collector,currency,amount,java.time.LocalDate.parse("2026-11-01"),19,urgency)
 @Test fun countsUrgencyAndCurrencyTotalsAndRanks(){
  val result=PromiseFollowUpCollectorSummaryBuilder.build(listOf(
   item("a","z",PromiseFollowUpUrgency.CRITICAL,"DOP",100),
   item("b","z",PromiseFollowUpUrgency.HIGH,"USD",200),
   item("c","a",PromiseFollowUpUrgency.CRITICAL,"EUR",50),
   item("d","z",PromiseFollowUpUrgency.LOW,"DOP",300)
  ))
  assertEquals("z",result.first().collectorId)
  val z=result.first()
  assertEquals(3,z.overdueCount);assertEquals(1,z.criticalCount);assertEquals(1,z.highCount);assertEquals(1,z.lowCount)
  assertEquals(400L,z.overdueMinorByCurrency["DOP"]);assertEquals(200L,z.overdueMinorByCurrency["USD"])
 }
 @Test fun emptyAndOverflow(){
  assertTrue(PromiseFollowUpCollectorSummaryBuilder.build(emptyList()).isEmpty())
  try { PromiseFollowUpCollectorSummaryBuilder.build(listOf(item("a","x",PromiseFollowUpUrgency.CRITICAL,"DOP",Long.MAX_VALUE),item("b","x",PromiseFollowUpUrgency.LOW,"DOP",1)));fail() }
  catch(_:ArithmeticException){}
 }
}
