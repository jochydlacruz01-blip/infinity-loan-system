package com.infinityloan.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
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
    val createdAt: Long = System.currentTimeMillis()
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

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY fullName")
    fun observeAll(): Flow<List<ClientEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(client: ClientEntity)

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ClientEntity?
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
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE operationId = :operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): PaymentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: PaymentEntity)

    @Query("SELECT * FROM loans WHERE id = :loanId LIMIT 1")
    suspend fun findLoan(loanId: String): LoanEntity?

    @Query("UPDATE loans SET balanceMinor = :newBalance, status = :newStatus WHERE id = :loanId")
    suspend fun updateLoanBalance(loanId: String, newBalance: Long, newStatus: String)

    @Transaction
    suspend fun postPayment(payment: PaymentEntity): PaymentPostResult {
        if (payment.amountMinor <= 0L) return PaymentPostResult.INVALID_AMOUNT
        if (findByOperationId(payment.operationId) != null) return PaymentPostResult.DUPLICATE
        val loan = findLoan(payment.loanId) ?: return PaymentPostResult.LOAN_NOT_FOUND
        if (loan.status != "ACTIVE") return PaymentPostResult.LOAN_NOT_ACTIVE
        if (payment.amountMinor > loan.balanceMinor) return PaymentPostResult.EXCEEDS_BALANCE
        insert(payment)
        val remaining = loan.balanceMinor - payment.amountMinor
        updateLoanBalance(loan.id, remaining, if (remaining == 0L) "PAID" else "ACTIVE")
        return PaymentPostResult.POSTED
    }
}

enum class PaymentPostResult {
    POSTED, DUPLICATE, INVALID_AMOUNT, LOAN_NOT_FOUND, LOAN_NOT_ACTIVE, EXCEEDS_BALANCE
}

@Database(
    entities = [ClientEntity::class, LoanEntity::class, PaymentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class InfinityDatabase : RoomDatabase() {
    abstract fun clients(): ClientDao
    abstract fun loans(): LoanDao
    abstract fun payments(): PaymentDao

    companion object {
        @Volatile private var INSTANCE: InfinityDatabase? = null

        fun get(context: Context): InfinityDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    InfinityDatabase::class.java,
                    "infinity_loan_system.db"
                ).build().also { INSTANCE = it }
            }
    }
}
```
