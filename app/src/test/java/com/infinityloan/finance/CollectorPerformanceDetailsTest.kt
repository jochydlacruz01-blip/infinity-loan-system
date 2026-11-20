package com.infinityloan.finance

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CollectorPerformanceDetailsTest {
    private val day=LocalDate.parse("2026-11-20")
    private fun row(id:String,status:CollectionVisitStatus, date:LocalDate=day)=CollectionVisitRecord(id,"l$id","c$id","collector",date,status)

    @Test fun calculatesDetailedRatesAndIgnoresOutOfRange() {
        val rows=listOf(row("1",CollectionVisitStatus.ATTEMPTED),row("2",CollectionVisitStatus.CONTACTED),
            row("3",CollectionVisitStatus.PROMISE_TO_PAY),row("4",CollectionVisitStatus.PAID),
            row("5",CollectionVisitStatus.UNREACHABLE),row("6",CollectionVisitStatus.CANCELLED),
            row("7",CollectionVisitStatus.PAID,day.plusDays(1)))
        val detail=CollectorPerformanceDetails.build(rows,day,day).single()
        assertEquals(5,detail.scheduled)
        assertEquals(1,detail.cancelled)
        assertEquals(5,detail.completedVisits)
        assertEquals(1,detail.contactAttempts)
        assertEquals(3,detail.successfulContacts)
        assertEquals(1,detail.promisesToPay)
        assertEquals(1,detail.paidVisits)
        assertEquals(1,detail.unreachableVisits)
        assertEquals(60,detail.contactRatePercent)
        assertEquals(33,detail.promiseConversionPercent)
        assertEquals(20,detail.paidVisitRatePercent)
    }

    @Test fun emptyAndCancelledOnlyHaveSafeRates() {
        val detail=CollectorPerformanceDetails.build(listOf(row("c",CollectionVisitStatus.CANCELLED)),day,day).single()
        assertEquals(0,detail.scheduled)
        assertEquals(0,detail.contactRatePercent)
        assertEquals(0,detail.promiseConversionPercent)
        assertEquals(0,detail.paidVisitRatePercent)
    }
}
