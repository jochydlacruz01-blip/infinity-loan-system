package com.infinityloan.finance

import java.time.LocalDate

/** Rules for moving a visit to another date without altering its identity or terminal state. */
object CollectionVisitRescheduling {
    fun reschedule(record: CollectionVisitRecord, newDate: LocalDate, note: String = record.outcomeNote): CollectionVisitRecord {
        CollectionVisitWorkflow.validate(record)
        require(note.length <= 2000) { "La nota excede el límite" }
        require(record.status != CollectionVisitStatus.PAID && record.status != CollectionVisitStatus.CANCELLED) {
            "Una visita cerrada no puede reprogramarse"
        }
        require(newDate != record.plannedDate) { "La visita ya está programada para esa fecha" }
        return record.copy(plannedDate=newDate, outcomeNote=note.trim())
    }

    /** Detects same-collector, same-day collisions in a proposed schedule. */
    fun conflicts(records: Iterable<CollectionVisitRecord>): List<Pair<String, LocalDate>> {
        val rows=records.filter { it.status != CollectionVisitStatus.CANCELLED && it.status != CollectionVisitStatus.PAID }
        rows.forEach(CollectionVisitWorkflow::validate)
        return rows.groupBy { it.collectorId to it.plannedDate }
            .filterValues { it.size > 1 }
            .keys.sortedWith(compareBy<Pair<String,LocalDate>>{it.first}.thenBy{it.second})
    }
}
