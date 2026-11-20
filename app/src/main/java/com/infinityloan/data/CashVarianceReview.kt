package com.infinityloan.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import com.infinityloan.security.Permission
import com.infinityloan.security.StaffAuthenticationService
import com.infinityloan.security.StaffSession
import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class CashVarianceDecision {
    APPROVED,
    REJECTED,
    REQUIRES_INVESTIGATION
}

/** Append-only supervisor decision with an immutable snapshot of the reviewed close. */
@Entity(
    tableName = "cash_variance_reviews",
    foreignKeys = [
        ForeignKey(entity = CashSessionEntity::class, parentColumns = ["sessionId"], childColumns = ["sessionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = StaffAccountEntity::class, parentColumns = ["id"], childColumns = ["reviewerStaffId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("sessionId"), Index("reviewerStaffId"), Index("reviewedAt")]
)
data class CashVarianceReviewEntity(
    @PrimaryKey val reviewId: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val reviewerStaffId: String,
    val decision: String,
    val comment: String,
    val currency: String,
    val expectedCashMinor: Long,
    val countedCashMinor: Long,
    val varianceMinor: Long,
    val reviewedAt: Long
)

@Dao
interface CashVarianceReviewDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(review: CashVarianceReviewEntity)

    @Query("SELECT * FROM cash_variance_reviews WHERE sessionId = :sessionId ORDER BY reviewedAt, reviewId")
    fun observeForSession(sessionId: String): Flow<List<CashVarianceReviewEntity>>

    @Query("SELECT * FROM cash_variance_reviews WHERE sessionId = :sessionId ORDER BY reviewedAt, reviewId")
    suspend fun findForSession(sessionId: String): List<CashVarianceReviewEntity>
}

/**
 * Authorization is revalidated against the persisted staff account for every
 * review. Each decision is appended; prior decisions are never overwritten.
 */
class CashVarianceReviewService(
    private val database: InfinityDatabase,
    private val authentication: StaffAuthenticationService,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun review(
        actor: StaffSession,
        sessionId: String,
        decision: CashVarianceDecision,
        comment: String
    ): CashVarianceReviewEntity = database.withTransaction {
        require(authentication.hasPermission(actor, Permission.CASH_VARIANCE_REVIEW)) {
            "No tienes permiso para revisar diferencias de caja."
        }
        require(sessionId.isNotBlank()) { "La sesión de caja es obligatoria." }
        require(comment.isNotBlank()) { "La revisión requiere una observación." }
        require(comment.trim().length <= 1000) {
            "La observación no puede superar los 1000 caracteres."
        }

        val row = database.auditPersistedCashClosings().rows.firstOrNull { it.sessionId == sessionId }
            ?: throw IllegalArgumentException("La sesión de caja no existe.")
        require(row.issue == null) { "La sesión contiene datos inválidos y no puede aprobarse." }
        require(row.closed) { "Solo se pueden revisar sesiones cerradas." }
        require(actor.staffId != row.staffId) {
            "No puedes revisar una diferencia de una sesión de caja propia."
        }
        val expected = requireNotNull(row.expectedCashMinor)
        val counted = requireNotNull(row.countedCashMinor)
        val variance = requireNotNull(row.varianceMinor)
        require(variance != 0L) { "La sesión no presenta diferencias que requieran revisión." }

        val review = CashVarianceReviewEntity(
            sessionId = sessionId,
            reviewerStaffId = actor.staffId,
            decision = decision.name,
            comment = comment.trim(),
            currency = row.currency,
            expectedCashMinor = expected,
            countedCashMinor = counted,
            varianceMinor = variance,
            reviewedAt = clock()
        )
        database.cashVarianceReviews().insert(review)
        database.auditLogs().insert(
            AuditLogEntity(
                actorStaffId = actor.staffId,
                actorUsername = actor.username,
                action = "CASH_VARIANCE_REVIEWED",
                targetType = "CASH_SESSION",
                targetId = sessionId,
                details = "decision=${decision.name};currency=${row.currency};varianceMinor=$variance;reviewId=${review.reviewId}",
                createdAt = review.reviewedAt
            )
        )
        review
    }
}
