package com.infinityloan.finance

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PromiseFollowUpQueueTest {
 private fun record(id:String,date:String,currency:String="DOP",amount:Long=100,status:PromiseToPayStatus=PromiseToPayStatus.OPEN)=
  PromiseToPayRecord(promiseId=id,loanId="loan$id",clientId="client$id",collectorId="collector",promisedAmountMinor=amount,currency=currency,promisedDate=LocalDate.parse(date),status=status)
 @Test fun filtersAndClassifiesMissedOpenPromises(){
  val result=PromiseFollowUpQueue.build(listOf(
   record("30","2026-10-21"),record("15","2026-11-05"),record("7","2026-11-13"),
   record("1","2026-11-19"),record("today","2026-11-20"),
   record("closed","2026-10-01",status=PromiseToPayStatus.KEPT)
  ),LocalDate.parse("2026-11-20"))
  assertEquals(listOf("30","15","7","1"),result.map{it.promiseId})
  assertEquals(PromiseFollowUpUrgency.CRITICAL,result[0].urgency)
  assertEquals(PromiseFollowUpUrgency.HIGH,result[1].urgency)
  assertEquals(PromiseFollowUpUrgency.MEDIUM,result[2].urgency)
  assertEquals(PromiseFollowUpUrgency.LOW,result[3].urgency)
 }
 @Test fun rejectsDuplicateIds(){
  try { PromiseFollowUpQueue.build(listOf(record("dup","2026-11-01"),record("dup","2026-11-02")),LocalDate.parse("2026-11-20"));fail() }
  catch(_:IllegalArgumentException){}
 }
 @Test fun preservesCurrenciesAndDeterministicTieOrder(){
  val result=PromiseFollowUpQueue.build(listOf(record("z","2026-11-01","USD",50),record("a","2026-11-01","USD",200),record("d","2026-11-01","DOP",100)),LocalDate.parse("2026-11-20"))
  assertEquals(listOf("d","a","z"),result.map{it.promiseId})
  assertEquals("USD",result[1].currency)
 }
 @Test fun emptyQueue(){assertTrue(PromiseFollowUpQueue.build(emptyList(),LocalDate.parse("2026-11-20")).isEmpty())}
}
