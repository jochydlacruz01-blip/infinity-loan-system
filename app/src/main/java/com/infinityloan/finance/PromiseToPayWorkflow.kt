package com.infinityloan.finance

import java.time.LocalDate
import java.util.UUID

enum class PromiseToPayStatus { OPEN, KEPT, BROKEN, CANCELLED }

data class PromiseToPayRecord(
    val promiseId: String = UUID.randomUUID().toString(),
    val loanId: String,
    val clientId: String,
    val collectorId: String,
    val promisedAmountMinor: Long,
    val currency: String,
    val promisedDate: LocalDate,
    val status: PromiseToPayStatus = PromiseToPayStatus.OPEN,
    val note: String = ""
)

object PromiseToPayWorkflow {
    private val currencies = setOf("DOP", "USD", "EUR")
    fun validate(p: PromiseToPayRecord) {
        require(p.promiseId.isNotBlank() && p.loanId.isNotBlank() && p.clientId.isNotBlank() && p.collectorId.isNotBlank())
        require(p.promisedAmountMinor > 0)
        require(p.currency in currencies)
        require(p.note.length <= 2000)
    }
    fun changeStatus(p: PromiseToPayRecord, status: PromiseToPayStatus, note: String = p.note): PromiseToPayRecord {
        validate(p)
        require(note.length <= 2000)
        require(p.status == PromiseToPayStatus.OPEN) { "La promesa ya está cerrada" }
        require(status != PromiseToPayStatus.OPEN) { "Estado inválido" }
        return p.copy(status=status, note=note.trim())
    }
    fun openPromises(records: List<PromiseToPayRecord>, asOf: LocalDate): List<PromiseToPayRecord> =
        records.filter { it.status == PromiseToPayStatus.OPEN }
            .sortedWith(compareBy<PromiseToPayRecord> { !it.promisedDate.isBefore(asOf) }
                .thenBy { it.promisedDate }.thenBy { it.clientId }.thenBy { it.promiseId })
}
