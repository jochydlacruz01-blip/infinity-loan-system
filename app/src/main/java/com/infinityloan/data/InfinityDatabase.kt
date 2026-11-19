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
    LOAN_NOT_FOUND,
    LOAN_NOT_ACTIVE,
    CLIENT_MISMATCH,
    CURRENCY_MISMATCH,
    INSUFFICIENT_CREDIT,
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

enum class PaymentPostResult {
    POSTED, DUPLICATE, INVALID_AMOUNT, LOAN_NOT_FOUND, LOAN_NOT_ACTIVE, EXCEEDS_BALANCE,
    SCHEDULE_NOT_FOUND, INVALID_SCHEDULE, LEGACY_RECONCILIATION_REQUIRED
}
enum class CreditPaymentResult {
    POSTED,
    DUPLICATE,
    INVALID_AMOUNT,
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
        PaymentAllocationEntity::class
    ],
    version = 5,
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

    abstract fun clients(): ClientDao
    abstract fun loans(): LoanDao
    abstract fun payments(): PaymentDao
    abstract fun customerCredits(): CustomerCreditDao
    abstract fun installments(): LoanInstallmentDao
    abstract fun paymentAllocations(): PaymentAllocationDao

    companion object {
        @Volatile private var INSTANCE: InfinityDatabase? = null
        
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

        fun get(context: Context): InfinityDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    InfinityDatabase::class.java,
                    "infinity_loan_system.db"
)
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
.build().also { INSTANCE = it }
            }
    }
}
