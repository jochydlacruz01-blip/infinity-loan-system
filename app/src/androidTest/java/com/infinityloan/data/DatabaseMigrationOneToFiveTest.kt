package com.infinityloan.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseMigrationOneToNineTest {

    @Test
    fun completeMigrationPathPreservesLegacyCreditAndBuildsPaymentLedger() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE clients (
                        id TEXT NOT NULL PRIMARY KEY,
                        fullName TEXT NOT NULL,
                        documentNumber TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        address TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE loans (
                        id TEXT NOT NULL PRIMARY KEY,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE payments (
                        operationId TEXT NOT NULL PRIMARY KEY
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO clients (
                        id, fullName, documentNumber, phone, address, createdAt
                    ) VALUES ('client-1', 'Cliente legado', 'DOC-1', '', '', 1)
                """.trimIndent())
                db.execSQL("INSERT INTO loans(id, createdAt) VALUES ('loan-1', 0)")
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
            db.execSQL("PRAGMA foreign_keys = ON")

            InfinityDatabase.MIGRATION_1_2.migrate(db)
            db.execSQL("""
                INSERT INTO customer_credit_movements (
                    operationId, clientId, loanId, currency, amountMinor, reference, createdAt
                ) VALUES ('credit-1', 'client-1', 'loan-1', 'DOP', 2500, 'Saldo legado', 2)
            """.trimIndent())

            InfinityDatabase.MIGRATION_2_3.migrate(db)
            InfinityDatabase.MIGRATION_3_4.migrate(db)
            InfinityDatabase.MIGRATION_4_5.migrate(db)
            InfinityDatabase.MIGRATION_5_6.migrate(db)
            InfinityDatabase.MIGRATION_6_7.migrate(db)
            InfinityDatabase.MIGRATION_7_8.migrate(db)
            InfinityDatabase.MIGRATION_8_9.migrate(db)

            db.query("""
                SELECT clientId, loanId, currency, amountMinor
                FROM customer_credit_movements WHERE operationId = 'credit-1'
            """.trimIndent()).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("client-1", cursor.getString(0))
                assertEquals("loan-1", cursor.getString(1))
                assertEquals("DOP", cursor.getString(2))
                assertEquals(2500L, cursor.getLong(3))
            }

            db.execSQL("""
                INSERT INTO loan_installments (
                    loanId, number, dueDate, principalMinor, interestMinor,
                    totalMinor, remainingPrincipalMinor, status,
                    paidLateFeeMinor, paidChargesMinor, paidInterestMinor, paidPrincipalMinor
                ) VALUES (
                    'loan-1', 1, '1970-01-31', 10000, 500, 10500, 0, 'PENDING',
                    0, 0, 0, 0
                )
            """.trimIndent())
            db.execSQL("INSERT INTO payments(operationId) VALUES ('payment-1')")
            db.execSQL("""
                INSERT INTO payment_allocations (
                    operationId, loanId, installmentNumber, lateFeePaidMinor,
                    chargesPaidMinor, interestPaidMinor, principalPaidMinor
                ) VALUES ('payment-1', 'loan-1', 1, 0, 0, 500, 1000)
            """.trimIndent())

            db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'staff_accounts'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
            }

            db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'audit_logs'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
            }

            db.query("PRAGMA table_info(staff_accounts)").use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns += cursor.getString(1)
                assertTrue(columns.contains("failedLoginAttempts"))
                assertTrue(columns.contains("lockedUntil"))
            }

            db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'cash_sessions'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
            }

            db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'cash_movements'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
            }

            db.execSQL("""
                INSERT INTO cash_sessions (
                    sessionId, staffId, currency, openingMinor, openedAt, closedAt, countedClosingMinor
                ) VALUES ('cash-session-1', 'staff-1', 'DOP', 5000, 10, NULL, NULL)
            """.trimIndent())
            db.execSQL("""
                INSERT INTO cash_movements (
                    operationId, sessionId, type, amountMinor, reason, createdAt
                ) VALUES ('cash-movement-1', 'cash-session-1', 'CASH_IN', 1000, 'Prueba migración', 11)
            """.trimIndent())
            db.query("SELECT amountMinor FROM cash_movements WHERE operationId = 'cash-movement-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1000L, cursor.getLong(0))
            }

            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertTrue("Migration chain left foreign-key violations", !cursor.moveToFirst())
            }

            db.query("SELECT COUNT(*) FROM payment_allocations").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
            }

            // Complete the historical upgrade path through the current schema (v10).
            InfinityDatabase.MIGRATION_9_10.migrate(db)

            db.query("PRAGMA table_info(payment_reversals)").use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns += cursor.getString(1)
                assertEquals(
                    setOf(
                        "reversalId", "originalOperationId", "loanId",
                        "amountMinor", "reason", "actorStaffId", "createdAt"
                    ),
                    columns
                )
            }

            db.execSQL("""
                INSERT INTO payment_reversals (
                    reversalId, originalOperationId, loanId, amountMinor,
                    reason, actorStaffId, createdAt
                ) VALUES (
                    'reversal-1', 'payment-1', 'loan-1', 1500,
                    'Prueba de migración', 'staff-1', 12
                )
            """.trimIndent())

            db.query("""
                SELECT originalOperationId, loanId, amountMinor
                FROM payment_reversals WHERE reversalId = 'reversal-1'
            """.trimIndent()).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("payment-1", cursor.getString(0))
                assertEquals("loan-1", cursor.getString(1))
                assertEquals(1500L, cursor.getLong(2))
            }

            db.query("PRAGMA index_list(payment_reversals)").use { cursor ->
                val indexes = mutableSetOf<String>()
                while (cursor.moveToNext()) indexes += cursor.getString(1)
                assertTrue(
                    "Missing unique original-payment reversal index",
                    indexes.contains("index_payment_reversals_originalOperationId")
                )
                assertTrue(
                    "Missing loanId reversal index",
                    indexes.contains("index_payment_reversals_loanId")
                )
            }

            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertTrue("Migration v10 left foreign-key violations", !cursor.moveToFirst())
            }
        } finally {
            helper.close()
        }
    }
}
