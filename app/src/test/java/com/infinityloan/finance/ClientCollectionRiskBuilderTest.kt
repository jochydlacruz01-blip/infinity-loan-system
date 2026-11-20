package com.infinityloan.finance

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ClientCollectionRiskBuilderTest {
    private val today = LocalDate.parse("2026-11-20")
    private fun installment(loan:String, client:String, days:Long, amount:Long=100, currency:String="DOP") =
        ClientCollectionRiskInput(loan,client,currency,today.minusDays(days),amount)
    private fun promise(id:String, client:String, days:Long) =
        PromiseToPayRecord(id,"loan-$id",client,"collector",100,"DOP",today.minusDays(days))

    @Test fun scoresRepeatDelinquencyAndMissedPromisesWithCurrencySeparation() {
        val result = ClientCollectionRiskBuilder.build(
            listOf(installment("l1","c1",40),installment("l2","c1",12,250,"USD"),installment("l3","c2",2)),
            listOf(promise("p1","c1",8)),today
        )
        assertEquals("c1",result.first().clientId)
        assertEquals(ClientCollectionRiskLevel.CRITICAL,result.first().riskLevel)
        assertEquals(2,result.first().overdueInstallments)
        assertEquals(2,result.first().overdueLoans)
        assertEquals(1,result.first().missedPromises)
        assertEquals(100L,result.first().overdueTotalsMinorByCurrency["DOP"])
        assertEquals(250L,result.first().overdueTotalsMinorByCurrency["USD"])
        assertEquals("c2",result.last().clientId)
    }

    @Test fun excludesPaidAndNotYetDueAndReturnsEmpty() {
        assertTrue(ClientCollectionRiskBuilder.build(emptyList(),emptyList(),today).isEmpty())
        val result=ClientCollectionRiskBuilder.build(listOf(installment("paid","c1",5,0),installment("future","c2",-2)),emptyList(),today)
        assertTrue(result.isEmpty())
    }

    @Test fun rejectsInvalidInput() {
        try {
            ClientCollectionRiskBuilder.build(listOf(installment("l","c",1,currency="XXX")),emptyList(),today)
            fail("Expected invalid currency")
        } catch (_: IllegalArgumentException) {}
    }
}
