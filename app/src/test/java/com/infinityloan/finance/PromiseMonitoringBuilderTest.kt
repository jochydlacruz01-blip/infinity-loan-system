package com.infinityloan.finance

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PromiseMonitoringBuilderTest {
    private val date=LocalDate.parse("2026-11-20")
    private fun promise(id:String, days:Long, currency:String="DOP", status:PromiseToPayStatus=PromiseToPayStatus.OPEN, amount:Long=100) =
        PromiseToPayRecord(id,"l$id","c$id","staff1",amount,currency,date.plusDays(days),status)

    @Test fun categorizesOverdueTodayUpcomingAndTotalsByCurrency() {
        val result=PromiseMonitoringBuilder.build(listOf(promise("old",-3),promise("today",0),promise("next",5),promise("usd",2,"USD",amount=250),promise("closed",-1,status=PromiseToPayStatus.KEPT)),date)
        assertEquals(1,result.overdue)
        assertEquals(1,result.dueToday)
        assertEquals(2,result.upcoming)
        assertEquals(300L,result.currencyTotalsMinor["DOP"])
        assertEquals(250L,result.currencyTotalsMinor["USD"])
        assertEquals(4,result.items.size)
    }

    @Test fun configurableHorizonAndEmptyInput() {
        assertEquals(0,PromiseMonitoringBuilder.build(emptyList(),date).items.size)
        assertEquals(0,PromiseMonitoringBuilder.build(listOf(promise("far",8)),date,upcomingDays=3).items.size)
    }

    @Test fun duplicateIdsAndNegativeHorizonRejected() {
        try { PromiseMonitoringBuilder.build(listOf(promise("dup",1),promise("dup",2)),date);fail("Expected duplicate rejection") }catch(_:IllegalArgumentException){}
        try { PromiseMonitoringBuilder.build(emptyList(),date,-1);fail("Expected invalid horizon") }catch(_:IllegalArgumentException){}
    }
}
