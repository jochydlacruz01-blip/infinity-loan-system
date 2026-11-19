package com.infinityloan.data

import com.infinityloan.cash.CashMovement
import com.infinityloan.cash.CashMovementType
import com.infinityloan.cash.CashSession

/** Read-only, per-session cash close comparison. Amounts remain in minor currency units. */
data class PersistedCashCloseAuditRow(
    val sessionId: String,
    val staffId: String,
    val currency: String,
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
    val movementsBySession = sessions.associate { session ->
        session.sessionId to dao.findMovements(session.sessionId)
    }

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
                closed = session.closed,
                expectedCashMinor = session.expectedCashMinor,
                countedCashMinor = session.countedClosingMinor,
                varianceMinor = session.varianceMinor,
                issue = null
            )
        } catch (error: IllegalArgumentException) {
            PersistedCashCloseAuditRow(
                row.sessionId, row.staffId, row.currency, isClosed,
                null, row.countedClosingMinor, null,
                error.message ?: "Datos de caja inválidos."
            )
        } catch (error: ArithmeticException) {
            PersistedCashCloseAuditRow(
                row.sessionId, row.staffId, row.currency, isClosed,
                null, row.countedClosingMinor, null,
                "Desbordamiento aritmético al reconstruir el efectivo esperado."
            )
        }
    }
    return PersistedCashCloseAuditReport(rows.size, rows)
}
