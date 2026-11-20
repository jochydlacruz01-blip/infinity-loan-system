package com.infinityloan.finance

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ClientCollectionDossierBuilderTest {
    private val today=LocalDate.parse("2026-11-20")
    private fun visit(id:String, client:String="c1", status:CollectionVisitStatus=CollectionVisitStatus.CONTACTED) =
        CollectionVisitRecord(id,"loan1",client,"staff1",today.minusDays(1),status)
    private fun attempt(id:String, at:String, client:String="c1") =
        CollectionContactAttempt(id,"v1","loan1",client,"staff1",Instant.parse(at),CollectionContactChannel.PHONE,CollectionContactOutcome.CONTACTED)
    private fun promise(id:String, days:Long, client:String="c1", currency:String="DOP", amount:Long=100) =
        PromiseToPayRecord(id,"loan1",client,"staff1",amount,currency,today.plusDays(days))

    @Test fun combinesClientActivityAndCurrencySafeOpenPromiseTotals() {
        val result=ClientCollectionDossierBuilder.build("c1",listOf(visit("v1"),visit("v2","c2")),listOf(attempt("a1","2026-11-18T10:00:00Z"),attempt("a2","2026-11-19T10:00:00Z","c2")),listOf(promise("p1",-2),promise("p2",3,currency="USD",amount=500),promise("closed",0,status=PromiseToPayStatus.KEPT)),today)
        assertEquals(1,result.visitCount)
        assertEquals(1,result.contactCount)
        assertEquals("a1",result.contactAttempts.first().attemptId)
        assertEquals(1,result.overduePromises.size)
        assertEquals(100L,result.openPromiseTotalsMinorByCurrency["DOP"])
        assertEquals(500L,result.openPromiseTotalsMinorByCurrency["USD"])
        assertEquals(listOf("loan1"),result.loanIds)
    }

    @Test fun emptyClientAndValidation() {
        val empty=ClientCollectionDossierBuilder.build("missing",emptyList(),emptyList(),emptyList(),today)
        assertEquals(0,empty.visitCount)
        assertNull(empty.lastContactAt)
        try { ClientCollectionDossierBuilder.build(" ",emptyList(),emptyList(),emptyList(),today);fail("Expected client validation") } catch(_:IllegalArgumentException){}
        try { ClientCollectionDossierBuilder.build("c1",listOf(visit("dup"),visit("dup")),emptyList(),emptyList(),today);fail("Expected duplicate visit") } catch(_:IllegalArgumentException){}
    }
}
