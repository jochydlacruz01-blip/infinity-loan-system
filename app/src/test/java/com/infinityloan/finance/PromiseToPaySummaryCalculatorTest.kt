package com.infinityloan.finance

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PromiseToPaySummaryCalculatorTest {
 private fun record(id:String,date:String,status:PromiseToPayStatus=PromiseToPayStatus.OPEN,currency:String="DOP",amount:Long=100)=PromiseToPayRecord(promiseId=id,loanId="l",clientId="c",collectorId="s",promisedAmountMinor=amount,currency=currency,promisedDate=LocalDate.parse(date),status=status)
 @Test fun totalsAndOverdueAreSeparatedByCurrency(){
  val s=PromiseToPaySummaryCalculator.calculate(listOf(record("1","2026-11-19",amount=200),record("2","2026-11-21",currency="USD",amount=300),record("3","2026-11-18",PromiseToPayStatus.BROKEN)),LocalDate.parse("2026-11-20"))
  assertEquals(1,s.openCount);assertEquals(1,s.overdueCount);assertEquals(200L,s.overdueMinorByCurrency["DOP"]);assertEquals(300L,s.openMinorByCurrency["USD"]);assertEquals(1,s.brokenCount)
 }
 @Test fun duplicateIdsRejected(){try{PromiseToPaySummaryCalculator.calculate(listOf(record("x","2026-11-19"),record("x","2026-11-20")),LocalDate.parse("2026-11-20"));fail()}catch(_:IllegalArgumentException){}}
 @Test fun overflowRejected(){try{PromiseToPaySummaryCalculator.calculate(listOf(record("1","2026-11-19",amount=Long.MAX_VALUE),record("2","2026-11-19",amount=1)),LocalDate.parse("2026-11-20"));fail()}catch(_:ArithmeticException){}}
}