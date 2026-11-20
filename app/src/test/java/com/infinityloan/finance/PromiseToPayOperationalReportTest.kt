package com.infinityloan.finance

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PromiseToPayOperationalReportTest {
 private fun p(id:String,collector:String,date:String,currency:String="DOP",amount:Long=100,status:PromiseToPayStatus=PromiseToPayStatus.OPEN)=
  PromiseToPayRecord(promiseId=id,loanId="loan$id",clientId="client$id",collectorId=collector,promisedAmountMinor=amount,currency=currency,promisedDate=LocalDate.parse(date),status=status)
 @Test fun bucketsAndCurrencyTotalsAreCorrectAndClosedRecordsExcluded(){
  val result=PromiseToPayOperationalReport.build(listOf(
   p("1","b","2026-11-19",amount=200),
   p("2","b","2026-11-20",currency="USD",amount=300),
   p("3","b","2026-11-22",currency="EUR",amount=400),
   p("4","a","2026-11-19",amount=50),
   p("5","a","2026-11-18",amount=500,status=PromiseToPayStatus.BROKEN)
  ),LocalDate.parse("2026-11-20"))
  assertEquals(listOf("b","a"),result.map{it.collectorId})
  val b=result[0]
  assertEquals(1,b.overdue);assertEquals(1,b.dueToday);assertEquals(1,b.upcoming);assertEquals(3,b.totalOpen)
  assertEquals(200L,b.overdueMinorByCurrency["DOP"]);assertEquals(300L,b.openMinorByCurrency["USD"]);assertEquals(400L,b.openMinorByCurrency["EUR"])
  assertEquals(1,result[1].overdue)
 }
 @Test fun duplicatePromiseIdsRejected(){
  try { PromiseToPayOperationalReport.build(listOf(p("x","a","2026-11-19"),p("x","b","2026-11-20")),LocalDate.parse("2026-11-20"));fail() }
  catch(_:IllegalArgumentException){}
 }
 @Test fun emptyInputReturnsEmptyReport(){assertTrue(PromiseToPayOperationalReport.build(emptyList(),LocalDate.parse("2026-11-20")).isEmpty())}
 @Test fun amountOverflowIsRejected(){
  try { PromiseToPayOperationalReport.build(listOf(p("1","a","2026-11-19",amount=Long.MAX_VALUE),p("2","a","2026-11-19",amount=1)),LocalDate.parse("2026-11-20"));fail() }
  catch(_:ArithmeticException){}
 }
}
