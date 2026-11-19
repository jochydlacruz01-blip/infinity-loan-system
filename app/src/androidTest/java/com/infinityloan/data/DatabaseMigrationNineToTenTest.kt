package com.infinityloan.data

import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DatabaseMigrationNineToTenTest {

    @Test
    fun migrationCreatesReversalLedgerWithUniquePaymentAndRestrictiveForeignKeys() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val callback = object : SupportSQLiteOpenHelper.Callback(9) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE payments (operationId TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("CREATE TABLE loans (id TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("INSERT INTO payments(operationId) VALUES ('payment-1')")
                db.execSQL("INSERT INTO loans(id) VALUES ('loan-1')")
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(callback)
                .build()
        )

        try {
            val db = helper.writableDatabase
            InfinityDatabase.MIGRATION_9_10.migrate(db)

            db.execSQL("""
                INSERT INTO payment_reversals (
                    reversalId, originalOperationId, loanId, amountMinor,
                    reason, actorStaffId, createdAt
                ) VALUES (
                    'reversal-1', 'payment-1', 'loan-1', 500,
                    'Prueba de migración', 'staff-1', 100
                )
            """.trimIndent())

            db.query("SELECT COUNT(*) FROM payment_reversals").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
            }

            assertThrows(SQLiteConstraintException::class.java) {
                db.execSQL("""
                    INSERT INTO payment_reversals (
                        reversalId, originalOperationId, loanId, amountMinor,
                        reason, actorStaffId, createdAt
                    ) VALUES (
                        'reversal-2', 'payment-1', 'loan-1', 500,
                        'Reverso duplicado', 'staff-1', 101
                    )
                """.trimIndent())
            }

            assertThrows(SQLiteConstraintException::class.java) {
                db.execSQL("""
                    INSERT INTO payment_reversals (
                        reversalId, originalOperationId, loanId, amountMinor,
                        reason, actorStaffId, createdAt
                    ) VALUES (
                        'reversal-orphan', 'missing-payment', 'loan-1', 500,
                        'Pago inexistente', 'staff-1', 102
                    )
                """.trimIndent())
            }

            db.query("PRAGMA index_list(payment_reversals)").use { cursor ->
                val indexes = mutableSetOf<String>()
                while (cursor.moveToNext()) indexes.add(cursor.getString(1))
                assertEquals(
                    true,
                    "index_payment_reversals_originalOperationId" in indexes
                )
                assertEquals(true, "index_payment_reversals_loanId" in indexes)
            }
        } finally {
            helper.close()
        }
    }
}
