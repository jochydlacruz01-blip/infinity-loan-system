package com.infinityloan.finance

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PromiseToPaySummaryCalculatorTest {
 private fun record(id:String,date:String,status:PromiseToPayStatus=PromiseToPayStatus.OPEN,currency:String="DOP",amount:Long=100)=PromiseToPayRecord(promiseId=id,loanId="l",clientId="c",collectorId="s",promisedAmountMinor=amount,currency=currency,promisedDate=LocalDate.parse(date),status=status)
 @Test fun totalsAndOverdueAreSeparatedByCurrency(){
  val s=PromiseToPaySummaryCalculator.calculate(listOf(record("1","2026-11-19",amount=200),record("2","2026-11-21",currency="USD",amount=300),record("3","2026-11-18",PromiseToPayStatus.BROKEN)),LocalDate.parse("2026-11-20"))
  assertEquals(2,s.openCount);assertEquals(1,s.overdueCount);assertEquals(200L,s.overdueMinorByCurrency["DOP"]);assertEquals(300L,s.openMinorByCurrency["USD"]);assertEquals(1,s.brokenCount)
 }
 @Test fun duplicateIdsRejected(){try{PromiseToPaySummaryCalculator.calculate(listOf(record("x","2026-11-19"),record("x","2026-11-20")),LocalDate.parse("2026-11-20"));fail()}catch(_:IllegalArgumentException){}}
 @Test fun overflowRejected(){try{PromiseToPaySummaryCalculator.calculate(listOf(record("1","2026-11-19",amount=Long.MAX_VALUE),record("2","2026-11-19",amount=1)),LocalDate.parse("2026-11-20"));fail()}catch(_:ArithmeticException){}}
 @Test fun includesAllCurrenciesAndZeroTotalsForEmptyBuckets(){
  val s=PromiseToPaySummaryCalculator.calculate(listOf(
   record("d","2026-11-20",currency="DOP",amount=50),
   record("u","2026-11-20",currency="USD",amount=75),
   record("e","2026-11-19",currency="EUR",amount=25)
  ),LocalDate.parse("2026-11-20"))
  assertEquals(setOf("DOP","USD","EUR"),s.openMinorByCurrency.keys)
  assertEquals(50L,s.openMinorByCurrency["DOP"])
  assertEquals(75L,s.openMinorByCurrency["USD"])
  assertEquals(25L,s.openMinorByCurrency["EUR"])
  assertEquals(0L,s.overdueMinorByCurrency["DOP"])
  assertEquals(0L,s.overdueMinorByCurrency["USD"])
  assertEquals(25L,s.overdueMinorByCurrency["EUR"])
 }
 @Test fun resolvedPromisesDoNotContributeToOpenOrOverdueAmounts(){
  val s=PromiseToPaySummaryCalculator.calculate(listOf(
   record("k","2026-11-19",PromiseToPayStatus.KEPT,amount=500),
   record("b","2026-11-18",PromiseToPayStatus.BROKEN,amount=700),
   record("o","2026-11-19",PromiseToPayStatus.OPEN,amount=100)
  ),LocalDate.parse("2026-11-20"))
  assertEquals(1,s.openCount)
  assertEquals(1,s.overdueCount)
  assertEquals(1,s.keptCount)
  assertEquals(1,s.brokenCount)
  assertEquals(100L,s.openMinorByCurrency["DOP"])
  assertEquals(100L,s.overdueMinorByCurrency["DOP"])
 }
}
