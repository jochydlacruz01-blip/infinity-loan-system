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
        require(reason.trim().length <= 500) { "La descripción no puede superar 500 caracteres." }
    }

    val signedAmountMinor: Long
        get() = if (type == CashMovementType.CASH_IN) amountMinor else Math.negateExact(amountMinor)
}