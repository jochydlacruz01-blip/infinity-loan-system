package com.infinityloan.cash

/**
 * Modelo puro de caja: no sustituye todavía el almacenamiento persistente.
 * Los movimientos se registran como cantidades positivas con dirección explícita.
 */
enum class CashMovementType { CASH_IN, CASH_OUT }

data class CashMovement(
    val operationId: String,
    val type: CashMovementType,
    val amountMinor: Long,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(operationId.isNotBlank()) { "La operación requiere un identificador." }
        require(amountMinor > 0L) { "El movimiento debe ser mayor que cero." }
        require(reason.isNotBlank()) { "El movimiento requiere una descripción." }
    }

    val signedAmountMinor: Long
        get() = if (type == CashMovementType.CASH_IN) amountMinor else Math.negateExact(amountMinor)
}

data class CashSession(
    val sessionId: String,
    val staffId: String,
    val currency: String,
    val openingMinor: Long,
    val movements: List<CashMovement> = emptyList(),
    val closed: Boolean = false,
    val countedClosingMinor: Long? = null
) {
    init {
        require(sessionId.isNotBlank() && staffId.isNotBlank()) { "Caja y empleado son obligatorios." }
        require(currency in setOf("DOP", "USD", "EUR")) { "Moneda no admitida." }
        require(openingMinor >= 0L) { "El fondo inicial no puede ser negativo." }
        require(movements.map { it.operationId }.distinct().size == movements.size) {
            "No se permiten movimientos duplicados en la sesión."
        }
        require(!closed || countedClosingMinor != null) { "El cierre requiere efectivo contado." }
        require(countedClosingMinor == null || countedClosingMinor >= 0L) {
            "El efectivo contado no puede ser negativo."
        }
    }

    val expectedCashMinor: Long
        get() = movements.fold(openingMinor) { total, movement ->
            Math.addExact(total, movement.signedAmountMinor)
        }

    val varianceMinor: Long?
        get() = countedClosingMinor?.let { Math.subtractExact(it, expectedCashMinor) }

    fun addMovement(movement: CashMovement): CashSession {
        check(!closed) { "La sesión está cerrada." }
        require(movements.none { it.operationId == movement.operationId }) {
            "Movimiento duplicado."
        }
        return copy(movements = movements + movement)
    }

    fun close(countedMinor: Long): CashSession {
        check(!closed) { "La sesión ya está cerrada." }
        require(countedMinor >= 0L) { "El efectivo contado no puede ser negativo." }
        return copy(closed = true, countedClosingMinor = countedMinor)
    }
}
