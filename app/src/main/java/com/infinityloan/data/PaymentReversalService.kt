package com.infinityloan.data

import com.infinityloan.security.Permission
import com.infinityloan.security.StaffAuthenticationService
import com.infinityloan.security.StaffSession

/** Authenticated application boundary for payment reversals. */
class PaymentReversalService(
    private val database: InfinityDatabase,
    private val authentication: StaffAuthenticationService
) {
    suspend fun reverse(
        session: StaffSession,
        operationId: String,
        reason: String
    ): PaymentReversalResult {
        if (!authentication.hasPermission(session, Permission.PAYMENT_REVERSE)) {
            return PaymentReversalResult.INVALID_ACTOR
        }
        return database.reversePayment(
            operationId = operationId,
            actorStaffId = session.staffId,
            actorUsername = session.username,
            reason = reason
        )
    }
}
