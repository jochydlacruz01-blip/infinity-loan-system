package com.infinityloan.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Read-only projection used to audit persisted loan payments against cash receipts. */
data class PersistedPaymentForReconciliation(val operationId: String, val amountMinor: Long, val currency: String)

data class PersistedCashMovementForReconciliation(
    val operationId: String,
    val type: String,
    val amountMinor: Long,
    val sessionCurrency: String
)

@Entity(tableName = "cash_sessions", indices = [Index("staffId"), Index("openedAt")])
data class CashSessionEntity(
    @PrimaryKey val sessionId: String,
    val staffId: String,
    val currency: String,
    val openingMinor: Long,
    val openedAt: Long,
    val closedAt: Long? = null,
    val countedClosingMinor: Long? = null
)

@Entity(
    tableName = "cash_movements",
    foreignKeys = [ForeignKey(
        entity = CashSessionEntity::class,
        parentColumns = ["sessionId"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("sessionId"), Index("createdAt")]
)
data class CashMovementEntity(
    @PrimaryKey val operationId: String,
    val sessionId: String,
    val type: String,
    val amountMinor: Long,
    val reason: String,
    val createdAt: Long
)

@Dao
interface CashSessionDao {
    @Query("SELECT cash_movements.operationId AS operationId, cash_movements.type AS type, cash_movements.amountMinor AS amountMinor, cash_sessions.currency AS sessionCurrency FROM cash_movements INNER JOIN cash_sessions ON cash_movements.sessionId = cash_sessions.sessionId ORDER BY cash_movements.createdAt, cash_movements.operationId")
    suspend fun findAllMovementsForAudit(): List<PersistedCashMovementForReconciliation>

    @Query("SELECT payments.operationId AS operationId, payments.amountMinor AS amountMinor, loans.currency AS currency FROM payments INNER JOIN loans ON payments.loanId = loans.id ORDER BY payments.createdAt, payments.operationId")
    suspend fun findAllPaymentsForCashAudit(): List<PersistedPaymentForReconciliation>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: CashSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMovement(movement: CashMovementEntity)

    @Query("SELECT * FROM cash_sessions WHERE sessionId = :id LIMIT 1")
    suspend fun findSession(id: String): CashSessionEntity?

    @Query("SELECT * FROM cash_sessions WHERE staffId = :staffId AND currency = :currency AND closedAt IS NULL ORDER BY openedAt DESC LIMIT 1")
    suspend fun findOpenSession(staffId: String, currency: String): CashSessionEntity?

    @Query("SELECT * FROM cash_sessions WHERE closedAt IS NULL ORDER BY openedAt DESC")
    fun observeOpenSessions(): Flow<List<CashSessionEntity>>

    @Query("SELECT * FROM cash_sessions ORDER BY openedAt DESC")
    fun observeSessions(): Flow<List<CashSessionEntity>>

    @Query("SELECT * FROM cash_movements WHERE sessionId = :sessionId ORDER BY createdAt, operationId")
    fun observeMovements(sessionId: String): Flow<List<CashMovementEntity>>

    @Query("SELECT * FROM cash_movements WHERE sessionId = :sessionId ORDER BY createdAt, operationId")
    suspend fun findMovements(sessionId: String): List<CashMovementEntity>

    @Query("SELECT * FROM cash_movements WHERE operationId = :operationId LIMIT 1")
    suspend fun findMovementByOperation(operationId: String): CashMovementEntity?

    @Query("UPDATE cash_sessions SET closedAt = :closedAt, countedClosingMinor = :countedMinor WHERE sessionId = :sessionId AND closedAt IS NULL")
    suspend fun closeSession(sessionId: String, closedAt: Long, countedMinor: Long): Int
}
