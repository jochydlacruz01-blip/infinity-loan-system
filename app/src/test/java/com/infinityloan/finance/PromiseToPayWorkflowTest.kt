package com.infinityloan.finance
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class PromiseToPayWorkflowTest {
 private fun promise()=PromiseToPayRecord(promiseId="p",loanId="l",clientId="c",collectorId="s",promisedAmountMinor=1000,currency="DOP",promisedDate=LocalDate.parse("2026-11-20"))
 @Test fun statusClosureAndNote(){ val kept=PromiseToPayWorkflow.changeStatus(promise(),PromiseToPayStatus.KEPT,"Confirmado");assertEquals("Confirmado",kept.note);assertEquals(PromiseToPayStatus.KEPT,kept.status) }
 @Test fun closedPromiseCannotBeReopened(){try{PromiseToPayWorkflow.changeStatus(promise().copy(status=PromiseToPayStatus.BROKEN),PromiseToPayStatus.KEPT);fail()}catch(_:IllegalArgumentException){}}
 @Test fun rejectsInvalidCurrencyAndAmount(){try{PromiseToPayWorkflow.validate(promise().copy(currency="X"));fail()}catch(_:IllegalArgumentException){};try{PromiseToPayWorkflow.validate(promise().copy(promisedAmountMinor=0));fail()}catch(_:IllegalArgumentException){}}
 @Test fun overduePromisesSortFirst(){val today=LocalDate.parse("2026-11-20");val list=listOf(promise().copy(promiseId="future",promisedDate=today.plusDays(2)),promise().copy(promiseId="past",promisedDate=today.minusDays(1)));assertEquals("past",PromiseToPayWorkflow.openPromises(list,today).first().promiseId)}
}
