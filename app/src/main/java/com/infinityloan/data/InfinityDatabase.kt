package com.infinityloan.data
import androidx.room.withTransaction
import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.time.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode
import com.infinityloan.finance.InstallmentPaymentAllocator
import com.infinityloan.finance.LoanScheduleGenerator
import com.infinityloan.security.AccessPolicy
import com.infinityloan.security.AccessProfile
import com.infinityloan.security.Permission
import com.infinityloan.security.StaffRole

@Entity(
    tableName = "collection_visits",
    foreignKeys = [
        ForeignKey(entity = LoanEntity::class, parentColumns = ["id"], childColumns = ["loanId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ClientEntity::class, parentColumns = ["id"], childColumns = ["clientId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = StaffAccountEntity::class, parentColumns = ["id"], childColumns = ["collectorId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("loanId"), Index("clientId"), Index("collectorId"), Index("status"), Index("plannedDate")]
)
data class CollectionVisitEntity(
    @PrimaryKey val visitId: String,
    val loanId: String,
    val clientId: String,
    val collectorId: String,
    val plannedDate: String,
    val status: String = "SCHEDULED",
    val outcomeNote: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface CollectionVisitDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(visit: CollectionVisitEntity)

    @Query("SELECT * FROM collection_visits ORDER BY plannedDate, createdAt, visitId")
    fun observeAll(): Flow<List<CollectionVisitEntity>>

    @Query("SELECT * FROM collection_visits WHERE collectorId = :collectorId ORDER BY plannedDate, createdAt, visitId")
    fun observeForCollector(collectorId: String): Flow<List<CollectionVisitEntity>>

    @Query("SELECT * FROM collection_visits WHERE status NOT IN ('PAID','CANCELLED') ORDER BY plannedDate, createdAt")
    fun observeOpen(): Flow<List<CollectionVisitEntity>>

    @Query("SELECT * FROM collection_visits WHERE visitId = :visitId LIMIT 1")
    suspend fun findById(visitId: String): CollectionVisitEntity?

    @Query("""
        UPDATE collection_visits
        SET status = :status, outcomeNote = :note, updatedAt = :updatedAt
        WHERE visitId = :visitId AND status = :expectedStatus
    """)
    suspend fun transition(
        visitId: String, expectedStatus: String, status: String,
        note: String, updatedAt: Long
    ): Int
}

@Entity(
    tableName = "promises_to_pay",
    foreignKeys = [
        ForeignKey(entity = LoanEntity::class, parentColumns = ["id"], childColumns = ["loanId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ClientEntity::class, parentColumns = ["id"], childColumns = ["clientId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = StaffAccountEntity::class, parentColumns = ["id"], childColumns = ["collectorId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("loanId"), Index("clientId"), Index("collectorId"), Index("status"), Index("promisedDate")]
)
data class PromiseToPayEntity(
    @PrimaryKey val promiseId: String,
    val loanId: String,
    val clientId: String,
    val collectorId: String,
    val promisedAmountMinor: Long,
    val currency: String,
    val promisedDate: String,
    val status: String = "OPEN",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface PromiseToPayDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(promise: PromiseToPayEntity)

    @Query("SELECT * FROM promises_to_pay ORDER BY promisedDate, createdAt, promiseId")
    fun observeAll(): Flow<List<PromiseToPayEntity>>

    @Query("SELECT * FROM promises_to_pay WHERE collectorId = :collectorId ORDER BY promisedDate, createdAt, promiseId")
    fun observeForCollector(collectorId: String): Flow<List<PromiseToPayEntity>>

    @Query("SELECT * FROM promises_to_pay WHERE status = 'OPEN' ORDER BY promisedDate, createdAt, promiseId")
    fun observeOpen(): Flow<List<PromiseToPayEntity>>

    @Query("SELECT * FROM promises_to_pay WHERE loanId = :loanId ORDER BY createdAt DESC")
    fun observeForLoan(loanId: String): Flow<List<PromiseToPayEntity>>

    @Query("SELECT * FROM promises_to_pay WHERE promiseId = :promiseId LIMIT 1")
    suspend fun findById(promiseId: String): PromiseToPayEntity?

    @Query("""
        UPDATE promises_to_pay
        SET status = :newStatus, note = :note, updatedAt = :updatedAt
        WHERE promiseId = :promiseId AND status = 'OPEN'
    """)
    suspend fun closeOpenPromise(
        promiseId: String,
        newStatus: String,
        note: String,
        updatedAt: Long = System.currentTimeMillis()
    ): Int
}

@Entity(tableName = "clients", indices = [Index(value = ["documentNumber"], unique = true)])
data class ClientEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fullName: String,
    val documentNumber: String,
    val phone: String,
    val address: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "loans",
    foreignKeys = [ForeignKey(
        entity = ClientEntity::class,
        parentColumns = ["id"],
        childColumns = ["clientId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("clientId")]
)
data class LoanEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val principalMinor: Long,
    val balanceMinor: Long,
    val currency: String,
    val periodicRate: String,
    val periods: Int,
    val method: String,
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'MONTHLY'") val frequency: String = "MONTHLY",
    @ColumnInfo(defaultValue = "'1970-01-01'") val firstDueDate: String = "1970-01-01"
)


@Entity(
    tableName = "loan_installments",
    primaryKeys = ["loanId", "number"],
    foreignKeys = [ForeignKey(
        entity = LoanEntity::class,
        parentColumns = ["id"],
        childColumns = ["loanId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("loanId"), Index("dueDate"), Index("status")]
)
data class LoanInstallmentEntity(
    val loanId: String,
    val number: Int,
    val dueDate: String,
    val principalMinor: Long,
    val interestMinor: Long,
    val totalMinor: Long,
    val remainingPrincipalMinor: Long,
    val status: String = "PENDING",
    @ColumnInfo(defaultValue = "0") val paidLateFeeMinor: Long = 0L,
    @ColumnInfo(defaultValue = "0") val paidChargesMinor: Long = 0L,
    @ColumnInfo(defaultValue = "0") val paidInterestMinor: Long = 0L,
    @ColumnInfo(defaultValue = "0") val paidPrincipalMinor: Long = 0L
)

@Entity(
    tableName = "payment_allocations",
    primaryKeys = ["operationId", "loanId", "installmentNumber"],
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["operationId"],
            childColumns = ["operationId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = LoanInstallmentEntity::class,
            parentColumns = ["loanId", "number"],
            childColumns = ["loanId", "installmentNumber"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("operationId"),
        Index(value = ["loanId", "installmentNumber"])
    ]
)
data class PaymentAllocationEntity(
    val operationId: String,
    val loanId: String,
    val installmentNumber: Int,
    val lateFeePaidMinor: Long,
    val chargesPaidMinor: Long,
    val interestPaidMinor: Long,
    val principalPaidMinor: Long
)

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(
        entity = LoanEntity::class,
        parentColumns = ["id"],
        childColumns = ["loanId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("loanId")]
)
data class PaymentEntity(
    @PrimaryKey val operationId: String,
    val loanId: String,
    val amountMinor: Long,
    val reference: String,
    val createdAt: Long = System.currentTimeMillis()
)
@Entity(
    tableName = "customer_credit_movements",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns = ["loanId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["clientId", "currency"]),
        Index(value = ["loanId"])
    ]
)
data class CustomerCreditMovementEntity(
    @PrimaryKey val operationId: String,
    val clientId: String,
    val loanId: String,
    val currency: String,
    val amountMinor: Long,
    val reference: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payment_reversals",
    foreignKeys = [
        ForeignKey(entity = PaymentEntity::class, parentColumns = ["operationId"], childColumns = ["originalOperationId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = LoanEntity::class, parentColumns = ["id"], childColumns = ["loanId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["originalOperationId"], unique = true), Index("loanId")]
)
data class PaymentReversalEntity(
    @PrimaryKey val reversalId: String,
    val originalOperationId: String,
    val loanId: String,
    val amountMinor: Long,
    val reason: String,
    val actorStaffId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface PaymentReversalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reversal: PaymentReversalEntity)

    @Query("SELECT * FROM payment_reversals WHERE originalOperationId = :operationId LIMIT 1")
    suspend fun findByOriginalOperationId(operationId: String): PaymentReversalEntity?

    @Query("SELECT originalOperationId FROM payment_reversals")
    suspend fun findAllReversedPaymentIds(): List<String>

    @Query("SELECT originalOperationId FROM payment_reversals ORDER BY createdAt DESC")
    fun observeReversedOperationIds(): Flow<List<String>>
}

data class StaffAccountSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val role: String,
    val isActive: Boolean,
    val createdAt: Long,
    val lastLoginAt: Long?
)

@Entity(
    tableName = "staff_accounts",
    indices = [Index(value = ["username"], unique = true)]
)
data class StaffAccountEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val username: String,
    val displayName: String,
    val role: String,
    val grants: String = "",
    val denials: String = "",
    val passwordAlgorithm: String,
    val passwordIterations: Int,
    val passwordSaltBase64: String,
    val passwordHashBase64: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val failedLoginAttempts: Int = 0,
    @ColumnInfo(defaultValue = "NULL") val lockedUntil: Long? = null
)

@Entity(
    tableName = "audit_logs",
    indices = [Index(value = ["createdAt"]), Index(value = ["actorStaffId"])]
)
data class AuditLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val actorStaffId: String?,
    val actorUsername: String,
    val action: String,
    val targetType: String,
    val targetId: String?,
    val details: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: AuditLogEntity)

    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE actorStaffId = :staffId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun findByActor(staffId: String, limit: Int): List<AuditLogEntity>
}

@Dao
interface StaffAccountDao {
    @Query("SELECT * FROM staff_accounts WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): StaffAccountEntity?

    @Query("SELECT * FROM staff_accounts WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): StaffAccountEntity?

    @Query("SELECT COUNT(*) FROM staff_accounts")
    suspend fun count(): Int

    @Query("""
        SELECT id, username, displayName, role, isActive, createdAt, lastLoginAt
        FROM staff_accounts
        ORDER BY displayName COLLATE NOCASE
    """)
    suspend fun getAllSummaries(): List<StaffAccountSummary>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: StaffAccountEntity)

    @Query("UPDATE staff_accounts SET lastLoginAt = :timestamp WHERE id = :id")
    suspend fun updateLastLogin(id: String, timestamp: Long)

    @Query("""
        UPDATE staff_accounts
        SET failedLoginAttempts = failedLoginAttempts + 1,
            lockedUntil = CASE
                WHEN failedLoginAttempts + 1 >= :maxAttempts THEN :lockUntil
                ELSE lockedUntil
            END
        WHERE id = :id AND (lockedUntil IS NULL OR lockedUntil <= :now)
    """)
    suspend fun recordFailedLogin(
        id: String,
        now: Long,
        maxAttempts: Int,
        lockUntil: Long
    )

    @Query("""
        UPDATE staff_accounts
        SET failedLoginAttempts = 0, lockedUntil = NULL
        WHERE id = :id
    """)
    suspend fun resetFailedLogins(id: String)

    @Query("UPDATE staff_accounts SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)

    @Query("SELECT COUNT(*) FROM staff_accounts WHERE role = 'ADMINISTRATOR' AND isActive = 1")
    suspend fun countActiveAdministrators(): Int

    /**
     * Atomically protects the last active administrator from concurrent deactivation.
     * Returns -1 when the sole active administrator would be disabled, 0 if missing,
     * or the number of rows updated otherwise.
     */
    @Transaction
    suspend fun setActiveSafely(id: String, active: Boolean): Int {
        val target = findById(id) ?: return 0
        if (!active && target.role == "ADMINISTRATOR" && target.isActive &&
            countActiveAdministrators() <= 1
        ) return -1
        return setActiveRow(id, active)
    }

    @Query("UPDATE staff_accounts SET isActive = :active WHERE id = :id")
    suspend fun setActiveRow(id: String, active: Boolean): Int

    @Query("UPDATE staff_accounts SET grants = :grants, denials = :denials WHERE id = :id")
    suspend fun updatePermissions(id: String, grants: String, denials: String)

    @Transaction
    suspend fun createInitialAdministrator(account: StaffAccountEntity): Boolean {
        if (count() != 0) return false
        insert(account)
        return true
    }
}

@Dao
interface CustomerCreditDao {

    @Query("""
        SELECT amountMinor
        FROM customer_credit_movements
        WHERE clientId = :clientId
        AND currency = :currency
        ORDER BY createdAt, operationId
    """)
    suspend fun getMovementAmounts(
        clientId: String,
        currency: String
    ): List<Long>

    /**
     * Uses checked addition so an unrepresentable ledger balance fails explicitly
     * instead of being truncated or wrapped by a numeric aggregate.
     */
    suspend fun getBalance(clientId: String, currency: String): Long =
        getMovementAmounts(clientId, currency).fold(0L) { total, amount ->
            Math.addExact(total, amount)
        }

    @Query("""
        SELECT * FROM customer_credit_movements
        WHERE clientId = :clientId
        AND currency = :currency
        ORDER BY createdAt DESC
    """)
    fun observeMovements(
        clientId: String,
        currency: String
    ): Flow<List<CustomerCreditMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(
        movement: CustomerCreditMovementEntity
    )
        @Query("""
        SELECT * FROM customer_credit_movements
        WHERE operationId = :operationId
        LIMIT 1
    """)
    suspend fun findMovement(
        operationId: String
    ): CustomerCreditMovementEntity?

    @Query("""
        SELECT * FROM loans
        WHERE id = :loanId
        LIMIT 1
    """)
    suspend fun findCreditLoan(
        loanId: String
    ): LoanEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(
        movement: CustomerCreditMovementEntity
    ): Long

    @Transaction
    suspend fun applyCredit(
        operationId: String,
        clientId: String,
        loanId: String,
        currency: String,
        amountMinor: Long,
        reference: String
    ): CustomerCreditResult {

        if (operationId.isBlank()) return CustomerCreditResult.INVALID_OPERATION_ID
        if (clientId.isBlank() || loanId.isBlank()) return CustomerCreditResult.INVALID_OPERATION_ID
        if (currency !in setOf("DOP", "USD", "EUR")) return CustomerCreditResult.INVALID_CURRENCY
        if (reference.isBlank()) return CustomerCreditResult.INVALID_REFERENCE
        if (amountMinor <= 0L) {
            return CustomerCreditResult.INVALID_AMOUNT
        }

        if (findMovement(operationId) != null) {
            return CustomerCreditResult.DUPLICATE
        }

        val loan = findCreditLoan(loanId)
            ?: return CustomerCreditResult.LOAN_NOT_FOUND

        if (loan.clientId != clientId) {
            return CustomerCreditResult.CLIENT_MISMATCH
        }

        if (loan.currency != currency) {
            return CustomerCreditResult.CURRENCY_MISMATCH
        }
        if (loan.status != "ACTIVE") {
            return CustomerCreditResult.LOAN_NOT_ACTIVE
        }
        val available = getBalance(clientId, currency)

        if (available < amountMinor) {
            return CustomerCreditResult.INSUFFICIENT_CREDIT
        }

        val movement = CustomerCreditMovementEntity(
            operationId = operationId,
            clientId = clientId,
            loanId = loanId,
            currency = currency,
            amountMinor = -amountMinor,
            reference = reference
        )

        val inserted = insertIfAbsent(movement)

        if (inserted == -1L) {
            return CustomerCreditResult.DUPLICATE
        }

        return CustomerCreditResult.APPLIED
    }
}


enum class CustomerCreditResult {
    APPLIED,
    DUPLICATE,
    INVALID_AMOUNT,
    INVALID_REFERENCE,
    INVALID_OPERATION_ID,
    LOAN_NOT_FOUND,
    LOAN_NOT_ACTIVE,
    CLIENT_MISMATCH,
    CURRENCY_MISMATCH,
    INSUFFICIENT_CREDIT,
    INVALID_CURRENCY,
    SCHEDULE_NOT_FOUND,
    INVALID_SCHEDULE,
    LEGACY_RECONCILIATION_REQUIRED
}
@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY fullName")
    fun observeAll(): Flow<List<ClientEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(client: ClientEntity)

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ClientEntity?
    
        @Query("UPDATE clients SET fullName = :fullName, documentNumber = :documentNumber, phone = :phone, address = :address WHERE id = :id")
    suspend fun updateClient(
        id: String,
        fullName: String,
        documentNumber: String,
        phone: String,
        address: String
    )
}

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): LoanEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(loan: LoanEntity)

    @Query("UPDATE loans SET balanceMinor = :newBalance, status = :newStatus WHERE id = :loanId")
    suspend fun updateBalance(loanId: String, newBalance: Long, newStatus: String)
}


@Dao
interface LoanInstallmentDao {
    @Query("SELECT * FROM loan_installments ORDER BY dueDate, loanId, number")
    fun observeAll(): Flow<List<LoanInstallmentEntity>>

    @Query("SELECT * FROM loan_installments WHERE loanId = :loanId ORDER BY number")
    fun observeForLoan(loanId: String): Flow<List<LoanInstallmentEntity>>

    @Query("SELECT * FROM loan_installments WHERE loanId = :loanId ORDER BY number")
    suspend fun findForLoan(loanId: String): List<LoanInstallmentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(installments: List<LoanInstallmentEntity>)

    @Query("""
        UPDATE loan_installments
        SET paidLateFeeMinor = :lateFee,
            paidChargesMinor = :charges,
            paidInterestMinor = :interest,
            paidPrincipalMinor = :principal,
            status = :status
        WHERE loanId = :loanId AND number = :number
    """)
    suspend fun updatePaidAmounts(
        loanId: String,
        number: Int,
        lateFee: Long,
        charges: Long,
        interest: Long,
        principal: Long,
        status: String
    )

    @Query("UPDATE loan_installments SET status = :status WHERE loanId = :loanId AND number = :number")
    suspend fun updateStatus(loanId: String, number: Int, status: String)
}

@Dao
interface PaymentAllocationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(allocations: List<PaymentAllocationEntity>)

    @Query("""
        SELECT * FROM payment_allocations
        WHERE operationId = :operationId
        ORDER BY installmentNumber
    """)
    fun observeForPayment(operationId: String): Flow<List<PaymentAllocationEntity>>

    @Query("""
        SELECT * FROM payment_allocations
        WHERE operationId = :operationId
        ORDER BY installmentNumber
    """)
    suspend fun findForPayment(operationId: String): List<PaymentAllocationEntity>
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE operationId = :operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): PaymentEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(payment: PaymentEntity): Long

    @Query("SELECT * FROM loans WHERE id = :loanId LIMIT 1")
    suspend fun findLoan(loanId: String): LoanEntity?

    @Query("SELECT * FROM loan_installments WHERE loanId = :loanId ORDER BY number")
    suspend fun findInstallments(loanId: String): List<LoanInstallmentEntity>

    @Query("SELECT COUNT(*) FROM payments WHERE loanId = :loanId")
    suspend fun countPaymentsForLoan(loanId: String): Int

    @Query("""
        SELECT COUNT(*) FROM payments
        WHERE loanId = :loanId AND operationId != :operationId AND createdAt >= :createdAt
    """)
    suspend fun countPaymentsAtOrAfter(loanId: String, operationId: String, createdAt: Long): Int


    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLegacyInstallments(rows: List<LoanInstallmentEntity>)

    /**
     * Safely reconstructs only untouched legacy loans. Historical payments cannot
     * be allocated retroactively without a verified accounting reconciliation.
     */
    @Transaction
    suspend fun ensureLegacySchedule(loan: LoanEntity): PaymentPostResult? {
        if (findInstallments(loan.id).isNotEmpty()) return null
        if (countPaymentsForLoan(loan.id) > 0 || loan.balanceMinor != loan.principalMinor) {
            return PaymentPostResult.LEGACY_RECONCILIATION_REQUIRED
        }
        return try {
            val frequency = LoanScheduleGenerator.Frequency.valueOf(loan.frequency)
            val firstDueDate = LocalDate.parse(loan.firstDueDate)
            val principal = BigDecimal.valueOf(loan.principalMinor, 2)
            val rate = BigDecimal(loan.periodicRate)
            val schedule = LoanScheduleGenerator.generate(
                principal = principal,
                periodicRate = rate,
                periods = loan.periods,
                method = loan.method,
                frequency = frequency,
                firstDueDate = firstDueDate,
                scale = 2
            )
            val rows = schedule.map { item ->
                fun toMinor(value: BigDecimal): Long =
                    value.setScale(2, RoundingMode.UNNECESSARY)
                        .movePointRight(2).longValueExact()
                LoanInstallmentEntity(
                    loanId = loan.id,
                    number = item.number,
                    dueDate = item.dueDate.toString(),
                    principalMinor = toMinor(item.principal),
                    interestMinor = toMinor(item.interest),
                    totalMinor = toMinor(item.total),
                    remainingPrincipalMinor = toMinor(item.remainingPrincipal)
                )
            }
            val scheduledPrincipal = rows.fold(0L) { total, row ->
                Math.addExact(total, row.principalMinor)
            }
            if (rows.size != loan.periods || scheduledPrincipal != loan.principalMinor) {
                return PaymentPostResult.INVALID_SCHEDULE
            }
            insertLegacyInstallments(rows)
            null
        } catch (_: Exception) {
            PaymentPostResult.INVALID_SCHEDULE
        }
    }

    @Query("""
        UPDATE loan_installments
        SET paidLateFeeMinor = :lateFee,
            paidChargesMinor = :charges,
            paidInterestMinor = :interest,
            paidPrincipalMinor = :principal,
            status = :status
        WHERE loanId = :loanId AND number = :number
    """)
    suspend fun updateInstallmentPaid(
        loanId: String, number: Int, lateFee: Long, charges: Long,
        interest: Long, principal: Long, status: String
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllocations(rows: List<PaymentAllocationEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCreditMovement(row: CustomerCreditMovementEntity): Long

    @Query("UPDATE loans SET balanceMinor = :newBalance, status = :newStatus WHERE id = :loanId")
    suspend fun updateLoanBalance(loanId: String, newBalance: Long, newStatus: String)

    /**
     * Posts only against installments due on the current local date.
     * Any amount not yet contractually due is preserved as customer credit.
     * Loan balanceMinor tracks principal only, never gross payment amount.
     */
    @Transaction
    suspend fun postPayment(payment: PaymentEntity): PaymentPostResult {
        if (payment.operationId.isBlank()) return PaymentPostResult.INVALID_OPERATION_ID
        if (payment.reference.isBlank()) return PaymentPostResult.INVALID_REFERENCE
        if (payment.amountMinor <= 0L) return PaymentPostResult.INVALID_AMOUNT
        if (findByOperationId(payment.operationId) != null) return PaymentPostResult.DUPLICATE
        val loan = findLoan(payment.loanId) ?: return PaymentPostResult.LOAN_NOT_FOUND
        if (loan.status != "ACTIVE") return PaymentPostResult.LOAN_NOT_ACTIVE

        val scheduleResult = ensureLegacySchedule(loan)
        if (scheduleResult != null) return scheduleResult
        val allInstallments = findInstallments(loan.id)
        if (allInstallments.isEmpty()) return PaymentPostResult.SCHEDULE_NOT_FOUND

        val today = LocalDate.now()
        val dueRows = allInstallments.filter { row ->
            val dueDate = try { LocalDate.parse(row.dueDate) }
            catch (_: Exception) { return PaymentPostResult.INVALID_SCHEDULE }
            dueDate <= today
        }

        fun outstanding(contractual: Long, paid: Long): Long {
            if (contractual < 0L || paid < 0L || paid > contractual) {
                throw IllegalStateException("Los importes pagados de una cuota no son válidos.")
            }
            return contractual - paid
        }

        val due = try {
            dueRows.map { row ->
                InstallmentPaymentAllocator.DueInstallment(
                    number = row.number,
                    dueDate = LocalDate.parse(row.dueDate),
                    lateFeeDueMinor = 0L,
                    chargesDueMinor = 0L,
                    interestDueMinor = outstanding(row.interestMinor, row.paidInterestMinor),
                    principalDueMinor = outstanding(row.principalMinor, row.paidPrincipalMinor)
                )
            }
        } catch (_: Exception) {
            return PaymentPostResult.INVALID_SCHEDULE
        }

        val allocation = try {
            InstallmentPaymentAllocator.allocate(payment.amountMinor, due)
        } catch (_: Exception) {
            return PaymentPostResult.INVALID_SCHEDULE
        }

        // Ledger invariant: every received minor unit must be either allocated
        // to a contractual installment or preserved as customer credit.
        val accountedPayment = try {
            if (allocation.unappliedMinor < 0L) return PaymentPostResult.INVALID_SCHEDULE
            Math.addExact(allocation.totalAllocatedMinor, allocation.unappliedMinor)
        } catch (_: ArithmeticException) {
            return PaymentPostResult.INVALID_SCHEDULE
        }
        if (accountedPayment != payment.amountMinor) {
            return PaymentPostResult.INVALID_SCHEDULE
        }

        val principalPaid = try {
            allocation.allocations.fold(0L) { total, item ->
                Math.addExact(total, item.principalPaidMinor)
            }
        } catch (_: ArithmeticException) {
            return PaymentPostResult.INVALID_SCHEDULE
        }
        if (principalPaid > loan.balanceMinor) return PaymentPostResult.EXCEEDS_BALANCE

        val newBalance = loan.balanceMinor - principalPaid
        val allocationsByNumber = allocation.allocations.associateBy { it.installmentNumber }

        val updatedRows = try {
            dueRows.map { row ->
                val applied = allocationsByNumber[row.number]
                val late = Math.addExact(row.paidLateFeeMinor, applied?.lateFeePaidMinor ?: 0L)
                val charges = Math.addExact(row.paidChargesMinor, applied?.chargesPaidMinor ?: 0L)
                val interest = Math.addExact(row.paidInterestMinor, applied?.interestPaidMinor ?: 0L)
                val principal = Math.addExact(row.paidPrincipalMinor, applied?.principalPaidMinor ?: 0L)
                // Until contractual late-fee/charge fields exist, those buckets
                // must remain zero; never mark a row paid based on a prior paid amount.
                val isPaid = late == 0L && charges == 0L &&
                    interest == row.interestMinor && principal == row.principalMinor
                val status = when {
                    isPaid -> "PAID"
                    late > 0L || charges > 0L || interest > 0L || principal > 0L -> "PARTIAL"
                    else -> "PENDING"
                }
                row.copy(
                    paidLateFeeMinor = late,
                    paidChargesMinor = charges,
                    paidInterestMinor = interest,
                    paidPrincipalMinor = principal,
                    status = status
                )
            }
        } catch (_: ArithmeticException) {
            return PaymentPostResult.INVALID_SCHEDULE
        } catch (_: Exception) {
            return PaymentPostResult.INVALID_SCHEDULE
        }

        val allPaidAfter = allInstallments.all { original ->
            val updated = updatedRows.firstOrNull { it.number == original.number } ?: original
            updated.paidLateFeeMinor == 0L &&
                updated.paidChargesMinor == 0L &&
                updated.paidInterestMinor == updated.interestMinor &&
                updated.paidPrincipalMinor == updated.principalMinor
        }
        val newStatus = if (newBalance == 0L && allPaidAfter) "PAID" else "ACTIVE"

        if (insert(payment) == -1L) return PaymentPostResult.DUPLICATE
        val allocationRows = allocation.allocations.map { item ->
            PaymentAllocationEntity(
                operationId = payment.operationId,
                loanId = loan.id,
                installmentNumber = item.installmentNumber,
                lateFeePaidMinor = item.lateFeePaidMinor,
                chargesPaidMinor = item.chargesPaidMinor,
                interestPaidMinor = item.interestPaidMinor,
                principalPaidMinor = item.principalPaidMinor
            )
        }
        if (allocationRows.isNotEmpty()) insertAllocations(allocationRows)

        updatedRows.forEach { row ->
            updateInstallmentPaid(
                loan.id, row.number, row.paidLateFeeMinor, row.paidChargesMinor,
                row.paidInterestMinor, row.paidPrincipalMinor, row.status
            )
        }
        updateLoanBalance(loan.id, newBalance, newStatus)

        if (allocation.unappliedMinor > 0L) {
            val creditInserted = insertCreditMovement(
                CustomerCreditMovementEntity(
                    operationId = "PAYMENT-EXCESS-${payment.operationId}",
                    clientId = loan.clientId,
                    loanId = loan.id,
                    currency = loan.currency,
                    amountMinor = allocation.unappliedMinor,
                    reference = "Excedente del pago ${payment.operationId}"
                )
            )
            check(creditInserted != -1L) { "No se pudo registrar el excedente como crédito." }
        }
        return PaymentPostResult.POSTED
    }
}

enum class PaymentReversalResult {
    REVERSED, ALREADY_REVERSED, PAYMENT_NOT_FOUND, LOAN_NOT_FOUND, INVALID_AMOUNT,
    INVALID_ACTOR, INVALID_REASON, INVALID_CREDIT_LEDGER, LATER_PAYMENT_EXISTS,
    GENERATED_CREDIT_ALREADY_USED, ORIGINAL_CASH_SESSION_NOT_OPEN, INVALID_CASH_LEDGER,
    INVALID_ALLOCATION
}

enum class PaymentPostResult {
    POSTED, DUPLICATE, INVALID_AMOUNT, INVALID_REFERENCE, INVALID_OPERATION_ID, LOAN_NOT_FOUND, LOAN_NOT_ACTIVE, EXCEEDS_BALANCE,
    SCHEDULE_NOT_FOUND, INVALID_SCHEDULE, LEGACY_RECONCILIATION_REQUIRED
}
enum class CreditPaymentResult {
    POSTED,
    DUPLICATE,
    INVALID_AMOUNT,
    INVALID_REFERENCE,
    INVALID_OPERATION_ID,
    LOAN_NOT_FOUND,
    LOAN_NOT_ACTIVE,
    EXCEEDS_BALANCE,
    INSUFFICIENT_CREDIT,
    SCHEDULE_NOT_FOUND,
    INVALID_SCHEDULE,
    LEGACY_RECONCILIATION_REQUIRED
}
@Database(
    entities = [
        ClientEntity::class,
        LoanEntity::class,
        PaymentEntity::class,
        CustomerCreditMovementEntity::class,
        LoanInstallmentEntity::class,
        PaymentAllocationEntity::class,
        StaffAccountEntity::class,
        AuditLogEntity::class,
        CashSessionEntity::class,
        CashMovementEntity::class,
        PaymentReversalEntity::class,
        CashVarianceReviewEntity::class,
        PromiseToPayEntity::class
    ],
    version = 13,
    exportSchema = false
)

abstract class InfinityDatabase : RoomDatabase() {
    suspend fun postPaymentWithCustomerCredit(payment: PaymentEntity): CreditPaymentResult =
        withTransaction {
            if (payment.amountMinor <= 0L) return@withTransaction CreditPaymentResult.INVALID_AMOUNT
            if (payments().findByOperationId(payment.operationId) != null) {
                return@withTransaction CreditPaymentResult.DUPLICATE
            }
            val loan = loans().findById(payment.loanId)
                ?: return@withTransaction CreditPaymentResult.LOAN_NOT_FOUND
            if (loan.status != "ACTIVE") return@withTransaction CreditPaymentResult.LOAN_NOT_ACTIVE

            val available = customerCredits().getBalance(loan.clientId, loan.currency)
            if (available < payment.amountMinor) {
                return@withTransaction CreditPaymentResult.INSUFFICIENT_CREDIT
            }

            val result = payments().postPayment(payment)
            if (result != PaymentPostResult.POSTED) {
                return@withTransaction when (result) {
                    PaymentPostResult.DUPLICATE -> CreditPaymentResult.DUPLICATE
                    PaymentPostResult.INVALID_AMOUNT -> CreditPaymentResult.INVALID_AMOUNT
                    PaymentPostResult.INVALID_REFERENCE -> CreditPaymentResult.INVALID_REFERENCE
                    PaymentPostResult.INVALID_OPERATION_ID -> CreditPaymentResult.INVALID_OPERATION_ID
                    PaymentPostResult.LOAN_NOT_FOUND -> CreditPaymentResult.LOAN_NOT_FOUND
                    PaymentPostResult.LOAN_NOT_ACTIVE -> CreditPaymentResult.LOAN_NOT_ACTIVE
                    PaymentPostResult.EXCEEDS_BALANCE -> CreditPaymentResult.EXCEEDS_BALANCE
                    PaymentPostResult.SCHEDULE_NOT_FOUND -> CreditPaymentResult.SCHEDULE_NOT_FOUND
                    PaymentPostResult.INVALID_SCHEDULE -> CreditPaymentResult.INVALID_SCHEDULE
                    PaymentPostResult.LEGACY_RECONCILIATION_REQUIRED -> CreditPaymentResult.LEGACY_RECONCILIATION_REQUIRED
                    PaymentPostResult.POSTED -> CreditPaymentResult.POSTED
                }
            }

            val debit = CustomerCreditMovementEntity(
                operationId = "PAYMENT-CREDIT-${payment.operationId}",
                clientId = loan.clientId,
                loanId = loan.id,
                currency = loan.currency,
                amountMinor = -payment.amountMinor,
                reference = "Aplicación de crédito al pago ${payment.operationId}"
            )
            val debitInserted = customerCredits().insertIfAbsent(debit)
            check(debitInserted != -1L) { "No se pudo descontar el crédito aplicado." }
            CreditPaymentResult.POSTED
        }


    suspend fun reversePayment(
        operationId: String,
        actorStaffId: String,
        actorUsername: String,
        reason: String,
        reversedAt: Long = System.currentTimeMillis()
    ): PaymentReversalResult = withTransaction {
        if (actorStaffId.isBlank() || actorUsername.isBlank()) return@withTransaction PaymentReversalResult.INVALID_ACTOR
        if (reason.isBlank() || reason.trim().length > 500) return@withTransaction PaymentReversalResult.INVALID_REASON
        val persistedActor = staffAccounts().findById(actorStaffId)
            ?: return@withTransaction PaymentReversalResult.INVALID_ACTOR
        if (!persistedActor.isActive || persistedActor.username != actorUsername) {
            return@withTransaction PaymentReversalResult.INVALID_ACTOR
        }
        val actorRole = runCatching { StaffRole.valueOf(persistedActor.role) }.getOrNull()
            ?: return@withTransaction PaymentReversalResult.INVALID_ACTOR
        fun decodePermissions(encoded: String): Set<Permission> = encoded.split('|').mapNotNull { token ->
            runCatching { Permission.valueOf(token) }.getOrNull()
        }.toSet()
        val actorProfile = AccessProfile(
            role = actorRole,
            grants = decodePermissions(persistedActor.grants),
            denials = decodePermissions(persistedActor.denials)
        )
        if (!AccessPolicy.allows(actorProfile, Permission.PAYMENT_REVERSE)) {
            return@withTransaction PaymentReversalResult.INVALID_ACTOR
        }
        if (paymentReversals().findByOriginalOperationId(operationId) != null) {
            return@withTransaction PaymentReversalResult.ALREADY_REVERSED
        }
        val payment = payments().findByOperationId(operationId)
            ?: return@withTransaction PaymentReversalResult.PAYMENT_NOT_FOUND
        val loan = loans().findById(payment.loanId)
            ?: return@withTransaction PaymentReversalResult.LOAN_NOT_FOUND
        if (payments().countPaymentsAtOrAfter(loan.id, operationId, payment.createdAt) > 0) {
            return@withTransaction PaymentReversalResult.LATER_PAYMENT_EXISTS
        }

        val allocations = paymentAllocations().findForPayment(operationId)
        val generatedCredit = customerCredits().findMovement("PAYMENT-EXCESS-" + operationId)
        val appliedCredit = customerCredits().findMovement("PAYMENT-CREDIT-" + operationId)
        if (generatedCredit != null && (generatedCredit.amountMinor <= 0L ||
            generatedCredit.clientId != loan.clientId || generatedCredit.loanId != loan.id ||
            generatedCredit.currency != loan.currency)) {
            return@withTransaction PaymentReversalResult.INVALID_CREDIT_LEDGER
        }
        if (appliedCredit != null && (appliedCredit.amountMinor >= 0L ||
            appliedCredit.clientId != loan.clientId || appliedCredit.loanId != loan.id ||
            appliedCredit.currency != loan.currency)) {
            return@withTransaction PaymentReversalResult.INVALID_CREDIT_LEDGER
        }
        val generatedCreditMinor = generatedCredit?.amountMinor ?: 0L
        val availableCredit = try {
            customerCredits().getBalance(loan.clientId, loan.currency)
        } catch (_: ArithmeticException) {
            return@withTransaction PaymentReversalResult.INVALID_CREDIT_LEDGER
        }

        val cashReceipt = cashSessions().findMovementByOperation("CASH-PAY-" + operationId)
        val cashSession = cashReceipt?.let { cashSessions().findSession(it.sessionId) }
        val policy = PaymentReversalPolicy.evaluate(
            PaymentReversalPolicy.Request(
                paymentAmountMinor = payment.amountMinor,
                actorStaffId = actorStaffId,
                reason = reason,
                hasLaterPaymentsForLoan = false,
                generatedCreditMinor = generatedCreditMinor,
                availableCustomerCreditMinor = availableCredit,
                hasOriginalCashReceipt = cashReceipt != null,
                originalCashSessionIsOpen = cashSession != null && cashSession.closedAt == null &&
                    cashSessions().findOpenSession(cashSession.staffId, cashSession.currency)?.sessionId == cashSession.sessionId
            )
        )
        when (policy) {
            PaymentReversalPolicy.Result.ELIGIBLE -> Unit
            PaymentReversalPolicy.Result.INVALID_PAYMENT_AMOUNT -> return@withTransaction PaymentReversalResult.INVALID_AMOUNT
            PaymentReversalPolicy.Result.INVALID_ACTOR -> return@withTransaction PaymentReversalResult.INVALID_ACTOR
            PaymentReversalPolicy.Result.INVALID_REASON -> return@withTransaction PaymentReversalResult.INVALID_REASON
            PaymentReversalPolicy.Result.INVALID_CREDIT_LEDGER -> return@withTransaction PaymentReversalResult.INVALID_CREDIT_LEDGER
            PaymentReversalPolicy.Result.LATER_PAYMENT_EXISTS -> return@withTransaction PaymentReversalResult.LATER_PAYMENT_EXISTS
            PaymentReversalPolicy.Result.GENERATED_CREDIT_ALREADY_USED -> return@withTransaction PaymentReversalResult.GENERATED_CREDIT_ALREADY_USED
            PaymentReversalPolicy.Result.ORIGINAL_CASH_SESSION_NOT_OPEN -> return@withTransaction PaymentReversalResult.ORIGINAL_CASH_SESSION_NOT_OPEN
        }
        if (cashReceipt != null && (cashReceipt.type != "CASH_IN" ||
            cashReceipt.amountMinor != payment.amountMinor || cashSession == null ||
            cashSession.currency != loan.currency)) {
            return@withTransaction PaymentReversalResult.INVALID_CASH_LEDGER
        }

        val rows = installments().findForLoan(loan.id).associateBy { it.number }
        var principalToRestore = 0L
        var allocatedTotal = 0L
        try {
            for (allocation in allocations) {
                if (allocation.loanId != loan.id || allocation.lateFeePaidMinor < 0L ||
                    allocation.chargesPaidMinor < 0L || allocation.interestPaidMinor < 0L ||
                    allocation.principalPaidMinor < 0L) {
                    return@withTransaction PaymentReversalResult.INVALID_ALLOCATION
                }
                val row = rows[allocation.installmentNumber]
                    ?: return@withTransaction PaymentReversalResult.INVALID_ALLOCATION
                if (row.paidLateFeeMinor < allocation.lateFeePaidMinor ||
                    row.paidChargesMinor < allocation.chargesPaidMinor ||
                    row.paidInterestMinor < allocation.interestPaidMinor ||
                    row.paidPrincipalMinor < allocation.principalPaidMinor) {
                    return@withTransaction PaymentReversalResult.INVALID_ALLOCATION
                }
                val buckets = Math.addExact(
                    Math.addExact(allocation.lateFeePaidMinor, allocation.chargesPaidMinor),
                    Math.addExact(allocation.interestPaidMinor, allocation.principalPaidMinor)
                )
                allocatedTotal = Math.addExact(allocatedTotal, buckets)
                principalToRestore = Math.addExact(principalToRestore, allocation.principalPaidMinor)
            }
            if (Math.addExact(allocatedTotal, generatedCreditMinor) != payment.amountMinor) {
                return@withTransaction PaymentReversalResult.INVALID_ALLOCATION
            }
        } catch (_: ArithmeticException) {
            return@withTransaction PaymentReversalResult.INVALID_ALLOCATION
        }
        val restoredBalance = try {
            Math.addExact(loan.balanceMinor, principalToRestore)
        } catch (_: ArithmeticException) {
            return@withTransaction PaymentReversalResult.INVALID_ALLOCATION
        }
        if (restoredBalance < 0L || restoredBalance > loan.principalMinor) {
            return@withTransaction PaymentReversalResult.INVALID_ALLOCATION
        }

        for (allocation in allocations) {
            val row = rows.getValue(allocation.installmentNumber)
            val late = row.paidLateFeeMinor - allocation.lateFeePaidMinor
            val charges = row.paidChargesMinor - allocation.chargesPaidMinor
            val interest = row.paidInterestMinor - allocation.interestPaidMinor
            val principal = row.paidPrincipalMinor - allocation.principalPaidMinor
            val status = when {
                late == 0L && charges == 0L && interest == row.interestMinor && principal == row.principalMinor -> "PAID"
                late > 0L || charges > 0L || interest > 0L || principal > 0L -> "PARTIAL"
                else -> "PENDING"
            }
            payments().updateInstallmentPaid(loan.id, row.number, late, charges, interest, principal, status)
        }

        if (generatedCreditMinor > 0L) {
            val inserted = customerCredits().insertIfAbsent(
                CustomerCreditMovementEntity(
                    operationId = "PAYMENT-REVERSAL-CREDIT-" + operationId,
                    clientId = loan.clientId,
                    loanId = loan.id,
                    currency = loan.currency,
                    amountMinor = -generatedCreditMinor,
                    reference = "Reverso del excedente del pago " + operationId,
                    createdAt = reversedAt
                )
            )
            check(inserted != -1L) { "No se pudo compensar el crédito excedente." }
        }
        if (appliedCredit != null) {
            val inserted = customerCredits().insertIfAbsent(
                CustomerCreditMovementEntity(
                    operationId = "PAYMENT-REVERSAL-RESTORE-" + operationId,
                    clientId = loan.clientId,
                    loanId = loan.id,
                    currency = loan.currency,
                    amountMinor = Math.negateExact(appliedCredit.amountMinor),
                    reference = "Restitución del crédito usado en el pago " + operationId,
                    createdAt = reversedAt
                )
            )
            check(inserted != -1L) { "No se pudo restituir el crédito aplicado." }
        }

        if (cashReceipt != null && cashSession != null) {
            val existing = cashSessions().findMovements(cashSession.sessionId).map { movement ->
                com.infinityloan.cash.CashMovement(
                    movement.operationId, com.infinityloan.cash.CashMovementType.valueOf(movement.type),
                    movement.amountMinor, movement.reason, movement.createdAt
                )
            }
            val cashOutId = "CASH-REVERSAL-" + operationId
            val cashReason = "Reverso de cobro " + operationId + ": " + reason.trim()
            val next = com.infinityloan.cash.CashSession(
                cashSession.sessionId, cashSession.staffId, cashSession.currency,
                cashSession.openingMinor, movements = existing
            ).addMovement(
                com.infinityloan.cash.CashMovement(
                    cashOutId, com.infinityloan.cash.CashMovementType.CASH_OUT,
                    payment.amountMinor, cashReason, reversedAt
                )
            )
            check(next.expectedCashMinor >= 0L)
            cashSessions().insertMovement(
                CashMovementEntity(cashOutId, cashSession.sessionId, "CASH_OUT", payment.amountMinor, cashReason, reversedAt)
            )
        }

        val afterRows = installments().findForLoan(loan.id)
        val allPaid = afterRows.all { row ->
            row.paidLateFeeMinor == 0L && row.paidChargesMinor == 0L &&
                row.paidInterestMinor == row.interestMinor && row.paidPrincipalMinor == row.principalMinor
        }
        payments().updateLoanBalance(loan.id, restoredBalance, if (restoredBalance == 0L && allPaid) "PAID" else "ACTIVE")
        paymentReversals().insert(
            PaymentReversalEntity(
                reversalId = "REVERSAL-" + operationId,
                originalOperationId = operationId,
                loanId = loan.id,
                amountMinor = payment.amountMinor,
                reason = reason.trim(),
                actorStaffId = actorStaffId,
                createdAt = reversedAt
            )
        )
        auditLogs().insert(
            AuditLogEntity(
                actorStaffId = actorStaffId,
                actorUsername = actorUsername,
                action = "PAYMENT_REVERSED",
                targetType = "PAYMENT",
                targetId = operationId,
                details = "Monto=" + payment.amountMinor + "; moneda=" + loan.currency + "; motivo=" + reason.trim(),
                createdAt = reversedAt
            )
        )
        PaymentReversalResult.REVERSED
    }

    /**
     * Cash operations are serialized inside Room transactions and validated by the domain model.
     * Amounts are stored in minor units (integer cents/centavos).
     */
    /** Atomically posts a loan payment and its cash receipt against an open session. */
    suspend fun postCashLoanPayment(payment: PaymentEntity, sessionId: String): PaymentPostResult = withTransaction {
        if (payment.operationId.isBlank()) return@withTransaction PaymentPostResult.INVALID_OPERATION_ID
        if (payment.reference.isBlank()) return@withTransaction PaymentPostResult.INVALID_REFERENCE
        if (payment.amountMinor <= 0L) return@withTransaction PaymentPostResult.INVALID_AMOUNT
        if (payments().findByOperationId(payment.operationId) != null) return@withTransaction PaymentPostResult.DUPLICATE
        val loan = loans().findById(payment.loanId) ?: return@withTransaction PaymentPostResult.LOAN_NOT_FOUND
        if (loan.status != "ACTIVE") return@withTransaction PaymentPostResult.LOAN_NOT_ACTIVE
        val session = cashSessions().findSession(sessionId) ?: return@withTransaction PaymentPostResult.INVALID_SCHEDULE
        if (session.closedAt != null || session.currency != loan.currency) return@withTransaction PaymentPostResult.INVALID_SCHEDULE
        check(cashSessions().findOpenSession(session.staffId, session.currency)?.sessionId == sessionId) { "La sesión no es la caja abierta del empleado." }
        val result = payments().postPayment(payment)
        if (result != PaymentPostResult.POSTED) return@withTransaction result
        val cashOperationId = "CASH-PAY-" + payment.operationId
        check(cashSessions().findMovementByOperation(cashOperationId) == null) { "El movimiento de caja ya existe." }
        val existing = cashSessions().findMovements(sessionId).map { row ->
            com.infinityloan.cash.CashMovement(row.operationId, com.infinityloan.cash.CashMovementType.valueOf(row.type), row.amountMinor, row.reason, row.createdAt)
        }
        val reason = "Cobro de préstamo " + payment.loanId + " / " + payment.operationId
        val domain = com.infinityloan.cash.CashSession(session.sessionId, session.staffId, session.currency, session.openingMinor, movements = existing).addMovement(
            com.infinityloan.cash.CashMovement(cashOperationId, com.infinityloan.cash.CashMovementType.CASH_IN, payment.amountMinor, reason, payment.createdAt)
        )
        check(domain.expectedCashMinor >= 0L)
        cashSessions().insertMovement(CashMovementEntity(cashOperationId, sessionId, "CASH_IN", payment.amountMinor, reason, payment.createdAt))
        PaymentPostResult.POSTED
    }
    suspend fun openCashSession(session: CashSessionEntity) = withTransaction {
        require(session.sessionId.isNotBlank() && session.staffId.isNotBlank())
        require(session.currency in setOf("DOP", "USD", "EUR"))
        require(session.openingMinor >= 0L)
        require(session.closedAt == null && session.countedClosingMinor == null)
        check(cashSessions().findOpenSession(session.staffId, session.currency) == null) {
            "Ya existe una caja abierta para este empleado y moneda."
        }
        cashSessions().insertSession(session)
    }

    suspend fun postCashMovement(movement: CashMovementEntity) = withTransaction {
        require(movement.amountMinor > 0L && movement.reason.isNotBlank())
        require(movement.type in setOf("CASH_IN", "CASH_OUT"))
        val sessionRow = cashSessions().findSession(movement.sessionId)
            ?: error("La sesión de caja no existe.")
        check(sessionRow.closedAt == null) { "La caja está cerrada." }
        check(cashSessions().findOpenSession(sessionRow.staffId, sessionRow.currency)?.sessionId == sessionRow.sessionId) {
            "La sesión no es la caja abierta del empleado para esta moneda."
        }
        check(cashSessions().findMovementByOperation(movement.operationId) == null) {
            "La operación ya fue registrada."
        }
        val existing = cashSessions().findMovements(sessionRow.sessionId).map { row ->
            com.infinityloan.cash.CashMovement(
                operationId = row.operationId,
                type = com.infinityloan.cash.CashMovementType.valueOf(row.type),
                amountMinor = row.amountMinor,
                reason = row.reason,
                createdAt = row.createdAt
            )
        }
        val domainSession = com.infinityloan.cash.CashSession(
            sessionId = sessionRow.sessionId,
            staffId = sessionRow.staffId,
            currency = sessionRow.currency,
            openingMinor = sessionRow.openingMinor,
            movements = existing
        )
        val next = domainSession.addMovement(
            com.infinityloan.cash.CashMovement(
                operationId = movement.operationId,
                type = com.infinityloan.cash.CashMovementType.valueOf(movement.type),
                amountMinor = movement.amountMinor,
                reason = movement.reason,
                createdAt = movement.createdAt
            )
        )
        check(next.expectedCashMinor >= 0L)
        cashSessions().insertMovement(movement)
    }

    suspend fun closeCashSession(sessionId: String, countedMinor: Long, closedAt: Long = System.currentTimeMillis()) = withTransaction {
        require(countedMinor >= 0L)
        val row = cashSessions().findSession(sessionId) ?: error("La sesión de caja no existe.")
        check(row.closedAt == null) { "La sesión ya está cerrada." }
        val existing = cashSessions().findMovements(sessionId).map { movement ->
            com.infinityloan.cash.CashMovement(
                operationId = movement.operationId,
                type = com.infinityloan.cash.CashMovementType.valueOf(movement.type),
                amountMinor = movement.amountMinor,
                reason = movement.reason,
                createdAt = movement.createdAt
            )
        }
        val domain = com.infinityloan.cash.CashSession(
            sessionId = row.sessionId,
            staffId = row.staffId,
            currency = row.currency,
            openingMinor = row.openingMinor,
            movements = existing
        ).close(countedMinor)
        check(domain.expectedCashMinor >= 0L)
        check(cashSessions().closeSession(sessionId, closedAt, countedMinor) == 1) {
            "No se pudo cerrar la sesión de forma segura."
        }
    }

    abstract fun clients(): ClientDao
    abstract fun loans(): LoanDao
    abstract fun payments(): PaymentDao
    abstract fun customerCredits(): CustomerCreditDao
    abstract fun installments(): LoanInstallmentDao
    abstract fun paymentAllocations(): PaymentAllocationDao
    abstract fun staffAccounts(): StaffAccountDao
    abstract fun auditLogs(): AuditLogDao
    abstract fun cashSessions(): CashSessionDao
    abstract fun paymentReversals(): PaymentReversalDao
    abstract fun cashVarianceReviews(): CashVarianceReviewDao
    abstract fun promisesToPay(): PromiseToPayDao\n    abstract fun collectionVisits(): CollectionVisitDao

    companion object {
        @Volatile private var INSTANCE: InfinityDatabase? = null

        internal fun installAuditImmutabilityTriggers(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TRIGGER IF NOT EXISTS audit_logs_no_update BEFORE UPDATE ON audit_logs BEGIN SELECT RAISE(ABORT, 'audit logs are immutable'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS audit_logs_no_delete BEFORE DELETE ON audit_logs BEGIN SELECT RAISE(ABORT, 'audit logs are immutable'); END")
        }
        
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(
        database: SupportSQLiteDatabase
    ) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS customer_credit_movements (
                operationId TEXT NOT NULL PRIMARY KEY,
                clientId TEXT NOT NULL,
                loanId TEXT NOT NULL,
                currency TEXT NOT NULL,
                amountMinor INTEGER NOT NULL,
                reference TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(clientId)
                    REFERENCES clients(id)
                    ON UPDATE NO ACTION
                    ON DELETE RESTRICT
            )
        """.trimIndent())

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS
            index_customer_credit_movements_clientId_currency
            ON customer_credit_movements(clientId, currency)
        """.trimIndent())
    }
}
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(
        database: SupportSQLiteDatabase
    ) {
        database.execSQL("""
            CREATE TABLE customer_credit_movements_new (
                operationId TEXT NOT NULL PRIMARY KEY,
                clientId TEXT NOT NULL,
                loanId TEXT NOT NULL,
                currency TEXT NOT NULL,
                amountMinor INTEGER NOT NULL,
                reference TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(clientId)
                    REFERENCES clients(id)
                    ON UPDATE NO ACTION
                    ON DELETE RESTRICT,
                FOREIGN KEY(loanId)
                    REFERENCES loans(id)
                    ON UPDATE NO ACTION
                    ON DELETE RESTRICT
            )
        """.trimIndent())

        database.execSQL("""
            INSERT INTO customer_credit_movements_new (
                operationId,
                clientId,
                loanId,
                currency,
                amountMinor,
                reference,
                createdAt
            )
            SELECT
                operationId,
                clientId,
                loanId,
                currency,
                amountMinor,
                reference,
                createdAt
            FROM customer_credit_movements
        """.trimIndent())

        database.execSQL("""
            DROP TABLE customer_credit_movements
        """.trimIndent())

        database.execSQL("""
            ALTER TABLE customer_credit_movements_new
            RENAME TO customer_credit_movements
        """.trimIndent())

        database.execSQL("""
            CREATE INDEX
            index_customer_credit_movements_clientId_currency
            ON customer_credit_movements(clientId, currency)
        """.trimIndent())

        database.execSQL("""
            CREATE INDEX
            index_customer_credit_movements_loanId
            ON customer_credit_movements(loanId)
        """.trimIndent())
    }
}

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE loans ADD COLUMN frequency TEXT NOT NULL DEFAULT 'MONTHLY'"
        )
        database.execSQL(
            "ALTER TABLE loans ADD COLUMN firstDueDate TEXT NOT NULL DEFAULT '1970-01-01'"
        )
        database.execSQL("""
            UPDATE loans
            SET firstDueDate = COALESCE(
                strftime('%Y-%m-%d', createdAt / 1000, 'unixepoch', '+30 days'),
                '1970-01-01'
            )
        """.trimIndent())
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS loan_installments (
                loanId TEXT NOT NULL,
                number INTEGER NOT NULL,
                dueDate TEXT NOT NULL,
                principalMinor INTEGER NOT NULL,
                interestMinor INTEGER NOT NULL,
                totalMinor INTEGER NOT NULL,
                remainingPrincipalMinor INTEGER NOT NULL,
                status TEXT NOT NULL,
                PRIMARY KEY(loanId, number),
                FOREIGN KEY(loanId) REFERENCES loans(id)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS index_loan_installments_loanId ON loan_installments(loanId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_loan_installments_dueDate ON loan_installments(dueDate)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_loan_installments_status ON loan_installments(status)")
    }
}

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE loan_installments ADD COLUMN paidLateFeeMinor INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE loan_installments ADD COLUMN paidChargesMinor INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE loan_installments ADD COLUMN paidInterestMinor INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE loan_installments ADD COLUMN paidPrincipalMinor INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL("""
            CREATE TABLE payment_allocations (
                operationId TEXT NOT NULL,
                loanId TEXT NOT NULL,
                installmentNumber INTEGER NOT NULL,
                lateFeePaidMinor INTEGER NOT NULL,
                chargesPaidMinor INTEGER NOT NULL,
                interestPaidMinor INTEGER NOT NULL,
                principalPaidMinor INTEGER NOT NULL,
                PRIMARY KEY(operationId, loanId, installmentNumber),
                FOREIGN KEY(operationId) REFERENCES payments(operationId)
                    ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(loanId, installmentNumber)
                    REFERENCES loan_installments(loanId, number)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        database.execSQL(
            "CREATE INDEX index_payment_allocations_operationId ON payment_allocations(operationId)"
        )
        database.execSQL(
            "CREATE INDEX index_payment_allocations_loanId_installmentNumber ON payment_allocations(loanId, installmentNumber)"
        )
    }
}

internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS staff_accounts (
                id TEXT NOT NULL PRIMARY KEY,
                username TEXT NOT NULL,
                displayName TEXT NOT NULL,
                role TEXT NOT NULL,
                grants TEXT NOT NULL,
                denials TEXT NOT NULL,
                passwordAlgorithm TEXT NOT NULL,
                passwordIterations INTEGER NOT NULL,
                passwordSaltBase64 TEXT NOT NULL,
                passwordHashBase64 TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                lastLoginAt INTEGER
            )
        """.trimIndent())
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_staff_accounts_username ON staff_accounts(username)"
        )
    }
}

internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS audit_logs (
                id TEXT NOT NULL PRIMARY KEY,
                actorStaffId TEXT,
                actorUsername TEXT NOT NULL,
                action TEXT NOT NULL,
                targetType TEXT NOT NULL,
                targetId TEXT,
                details TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_audit_logs_createdAt ON audit_logs(createdAt)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_audit_logs_actorStaffId ON audit_logs(actorStaffId)"
        )
    }
}

internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE staff_accounts ADD COLUMN failedLoginAttempts INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE staff_accounts ADD COLUMN lockedUntil INTEGER DEFAULT NULL"
        )
    }
}

internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS cash_variance_reviews (
                reviewId TEXT NOT NULL PRIMARY KEY,
                sessionId TEXT NOT NULL,
                reviewerStaffId TEXT NOT NULL,
                decision TEXT NOT NULL,
                comment TEXT NOT NULL,
                currency TEXT NOT NULL,
                expectedCashMinor INTEGER NOT NULL,
                countedCashMinor INTEGER NOT NULL,
                varianceMinor INTEGER NOT NULL,
                reviewedAt INTEGER NOT NULL,
                FOREIGN KEY(sessionId) REFERENCES cash_sessions(sessionId)
                    ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(reviewerStaffId) REFERENCES staff_accounts(id)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS index_cash_variance_reviews_sessionId ON cash_variance_reviews(sessionId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_cash_variance_reviews_reviewerStaffId ON cash_variance_reviews(reviewerStaffId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_cash_variance_reviews_reviewedAt ON cash_variance_reviews(reviewedAt)")
    }
}

internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS payment_reversals (
                reversalId TEXT NOT NULL PRIMARY KEY,
                originalOperationId TEXT NOT NULL,
                loanId TEXT NOT NULL,
                amountMinor INTEGER NOT NULL,
                reason TEXT NOT NULL,
                actorStaffId TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(originalOperationId) REFERENCES payments(operationId)
                    ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(loanId) REFERENCES loans(id)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payment_reversals_originalOperationId ON payment_reversals(originalOperationId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_payment_reversals_loanId ON payment_reversals(loanId)")
    }
}

internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS cash_sessions (
                sessionId TEXT NOT NULL PRIMARY KEY,
                staffId TEXT NOT NULL,
                currency TEXT NOT NULL,
                openingMinor INTEGER NOT NULL,
                openedAt INTEGER NOT NULL,
                closedAt INTEGER,
                countedClosingMinor INTEGER
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS index_cash_sessions_staffId ON cash_sessions(staffId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_cash_sessions_openedAt ON cash_sessions(openedAt)")
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS cash_movements (
                operationId TEXT NOT NULL PRIMARY KEY,
                sessionId TEXT NOT NULL,
                type TEXT NOT NULL,
                amountMinor INTEGER NOT NULL,
                reason TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(sessionId) REFERENCES cash_sessions(sessionId)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS index_cash_movements_sessionId ON cash_movements(sessionId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_cash_movements_createdAt ON cash_movements(createdAt)")
    }
}

        fun get(context: Context): InfinityDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    InfinityDatabase::class.java,
                    "infinity_loan_system.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        installAuditImmutabilityTriggers(db)
                    }
                })
                .build().also { INSTANCE = it }
            }
    }
}

internal val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        InfinityDatabase.installAuditImmutabilityTriggers(database)
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS promises_to_pay (
                promiseId TEXT NOT NULL PRIMARY KEY,
                loanId TEXT NOT NULL,
                clientId TEXT NOT NULL,
                collectorId TEXT NOT NULL,
                promisedAmountMinor INTEGER NOT NULL,
                currency TEXT NOT NULL,
                promisedDate TEXT NOT NULL,
                status TEXT NOT NULL,
                note TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(loanId) REFERENCES loans(id)
                    ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(clientId) REFERENCES clients(id)
                    ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(collectorId) REFERENCES staff_accounts(id)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS index_promises_to_pay_loanId ON promises_to_pay(loanId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_promises_to_pay_clientId ON promises_to_pay(clientId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_promises_to_pay_collectorId ON promises_to_pay(collectorId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_promises_to_pay_status ON promises_to_pay(status)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_promises_to_pay_promisedDate ON promises_to_pay(promisedDate)")
    }
}


val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS collection_visits (
                visitId TEXT NOT NULL PRIMARY KEY,
                loanId TEXT NOT NULL,
                clientId TEXT NOT NULL,
                collectorId TEXT NOT NULL,
                plannedDate TEXT NOT NULL,
                status TEXT NOT NULL,
                outcomeNote TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(loanId) REFERENCES loans(id)
                    ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(clientId) REFERENCES clients(id)
                    ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(collectorId) REFERENCES staff_accounts(id)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS index_collection_visits_loanId ON collection_visits(loanId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_collection_visits_clientId ON collection_visits(clientId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_collection_visits_collectorId ON collection_visits(collectorId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_collection_visits_status ON collection_visits(status)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_collection_visits_plannedDate ON collection_visits(plannedDate)")
    }
}
