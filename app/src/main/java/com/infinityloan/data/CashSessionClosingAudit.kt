package com.infinityloan.data

import com.infinityloan.cash.CashMovement
import com.infinityloan.cash.CashMovementType
import com.infinityloan.cash.CashSession

/** Read-only, per-session cash close comparison. Amounts remain in minor currency units. */
data class PersistedCashCloseAuditRow(
    val sessionId: String,
    val staffId: String,
    val currency: String,
    val openingCashMinor: Long,
    val closed: Boolean,
    val expectedCashMinor: Long?,
    val countedCashMinor: Long?,
    val varianceMinor: Long?,
    val issue: String?
)

data class PersistedCashCloseAuditReport(
    val sessionsChecked: Int,
    val rows: List<PersistedCashCloseAuditRow>
)

/**
 * Reconstructs the expected cash from the opening float and persisted movements.
 * This is diagnostic only: it never closes sessions or changes financial records.
 */
suspend fun InfinityDatabase.auditPersistedCashClosings(): PersistedCashCloseAuditReport {
    val dao = cashSessions()
    val sessions = dao.findAllSessionsForAudit()
    val movementsBySession = dao.findAllMovementsForAudit().groupBy { it.sessionId }

    val rows = sessions.map { row ->
        val isClosed = row.closedAt != null
        val hasCount = row.countedClosingMinor != null
        try {
            require(isClosed == hasCount) {
                "El estado de cierre no coincide con la existencia del conteo físico."
            }
            val movements = movementsBySession[row.sessionId].orEmpty().map { movement ->
                CashMovement(
                    operationId = movement.operationId,
                    type = CashMovementType.valueOf(movement.type),
                    amountMinor = movement.amountMinor,
                    reason = movement.reason,
                    createdAt = movement.createdAt
                )
            }
            val session = CashSession(
                sessionId = row.sessionId,
                staffId = row.staffId,
                currency = row.currency,
                openingMinor = row.openingMinor,
                movements = movements,
                closed = isClosed,
                countedClosingMinor = row.countedClosingMinor
            )
            PersistedCashCloseAuditRow(
                sessionId = row.sessionId,
                staffId = row.staffId,
                currency = row.currency,
                openingCashMinor = row.openingMinor,
                closed = session.closed,
                expectedCashMinor = session.expectedCashMinor,
                countedCashMinor = session.countedClosingMinor,
                varianceMinor = session.varianceMinor,
                issue = null
            )
        } catch (error: IllegalArgumentException) {
            PersistedCashCloseAuditRow(
                row.sessionId, row.staffId, row.currency, row.openingMinor, isClosed,
                null, row.countedClosingMinor, null,
                error.message ?: "Datos de caja inválidos."
            )
        } catch (error: ArithmeticException) {
            PersistedCashCloseAuditRow(
                row.sessionId, row.staffId, row.currency, row.openingMinor, isClosed,
                null, row.countedClosingMinor, null,
                "Desbordamiento aritmético al reconstruir el efectivo esperado."
            )
        }
    }
    return PersistedCashCloseAuditReport(rows.size, rows)
}



/** Consolidated accountability totals; invalid sessions are reported, never silently counted. */
data class PersistedCashStaffAuditReport(
    val staffRows: List<com.infinityloan.cash.CashStaffReport>,
    val invalidSessionCount: Int,
    val invalidSessionIds: List<String>
)

/**
 * Builds employee/currency totals from persisted sessions using one batched read
 * of movements. Corrupt sessions are excluded from totals and listed for review.
 */
suspend fun InfinityDatabase.auditPersistedCashByStaff(): PersistedCashStaffAuditReport {
    val dao = cashSessions()
    val closingAudit = auditPersistedCashClosings()
    val movementsBySession = dao.findAllMovementsForAudit().groupBy { it.sessionId }

    val validSessions = closingAudit.rows.filter { it.issue == null }.map { row ->
        val movements = movementsBySession[row.sessionId].orEmpty().map { movement ->
            CashMovement(
                operationId = movement.operationId,
                type = CashMovementType.valueOf(movement.type),
                amountMinor = movement.amountMinor,
                reason = movement.reason,
                createdAt = movement.createdAt
            )
        }
        val openingMinor = row.openingCashMinor
        CashSession(
            sessionId = row.sessionId,
            staffId = row.staffId,
            currency = row.currency,
            openingMinor = openingMinor,
            movements = movements,
            closed = row.closed,
            countedClosingMinor = row.countedCashMinor
        )
    }

    val invalidIds = closingAudit.rows.filter { it.issue != null }.map { it.sessionId }
    return PersistedCashStaffAuditReport(
        staffRows = com.infinityloan.cash.buildCashStaffReport(validSessions),
        invalidSessionCount = invalidIds.size,
        invalidSessionIds = invalidIds
    )
}
