package com.infinityloan.finance

/**
 * Deterministic workload helper for suggesting collectors for new visits.
 * It does not replace authorized/manual assignment or persist any assignment.
 */
object CollectionWorkloadBalancer {
    fun suggestCollector(collectorIds: List<String>, assignedVisitCounts: Map<String, Int>): String {
        val eligible = collectorIds.filter { it.isNotBlank() }.distinct().sorted()
        require(eligible.isNotEmpty()) { "No hay cobradores disponibles" }
        eligible.forEach { id ->
            require((assignedVisitCounts[id] ?: 0) >= 0) { "Carga inválida para cobrador" }
        }
        return eligible.minWith(compareBy<String> { assignedVisitCounts[it] ?: 0 }.thenBy { it })
    }

    fun workload(collectorIds: List<String>, assignedVisitCounts: Map<String, Int>): Map<String, Int> =
        collectorIds.filter { it.isNotBlank() }.distinct().sorted()
            .associateWith { assignedVisitCounts[it] ?: 0 }
}
