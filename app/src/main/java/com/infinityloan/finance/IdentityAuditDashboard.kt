package com.infinityloan.finance

data class IdentityAuditCategorySummary(val type: IdentityIntegrityIssueType, val count: Int)
data class IdentityAuditDashboard(
    val totalFindings: Int,
    val categories: List<IdentityAuditCategorySummary>,
    val affectedKeys: List<String>,
    val clean: Boolean
)

/** Converts diagnostic findings into deterministic supervisor-facing summary data. */
object IdentityAuditDashboardBuilder {
    fun build(findings: List<IdentityIntegrityIssue>): IdentityAuditDashboard {
        val categories=findings.groupBy { it.type }.map { (type,items) ->
            IdentityAuditCategorySummary(type,items.size)
        }.sortedWith(compareByDescending<IdentityAuditCategorySummary>{it.count}.thenBy{it.type.name})
        return IdentityAuditDashboard(
            totalFindings=findings.size,
            categories=categories,
            affectedKeys=findings.map{it.key}.filter{it.isNotBlank()}.distinct().sorted(),
            clean=findings.isEmpty()
        )
    }
}
