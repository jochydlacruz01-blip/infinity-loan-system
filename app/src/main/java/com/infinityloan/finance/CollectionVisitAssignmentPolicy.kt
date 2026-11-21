package com.infinityloan.finance

import com.infinityloan.security.StaffRole

/**
 * Pure authorization rule for assigning field visits.
 * Database existence/activity is resolved by the persistence service and supplied here.
 */
object CollectionVisitAssignmentPolicy {
    fun resolve(
        actorRole: StaffRole,
        actorStaffId: String,
        requestedCollectorId: String,
        targetRole: String?,
        targetIsActive: Boolean
    ): String {
        require(actorStaffId.isNotBlank()) { "La sesión no identifica al empleado." }
        require(requestedCollectorId.isNotBlank()) { "Selecciona un cobrador responsable." }

        if (actorRole == StaffRole.COLLECTOR) {
            require(requestedCollectorId == actorStaffId) {
                "Un cobrador solo puede programar visitas para sí mismo."
            }
            return actorStaffId
        }

        require(actorRole == StaffRole.ADMINISTRATOR || actorRole == StaffRole.SUPERVISOR) {
            "Tu perfil no puede asignar visitas a otros cobradores."
        }
        require(targetIsActive && targetRole == StaffRole.COLLECTOR.name) {
            "Solo puedes asignar visitas a cobradores activos."
        }
        return requestedCollectorId
    }
}
