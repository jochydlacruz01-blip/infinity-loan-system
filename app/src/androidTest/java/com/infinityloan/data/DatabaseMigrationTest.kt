package com.infinityloan.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseMigrationTest {

    @Test
    fun migrationFourToFivePreservesInstallmentsAndCreatesAllocationLedger() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val callback = object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE payments (
                        operationId TEXT NOT NULL PRIMARY KEY
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE loan_installments (
                        loanId TEXT NOT NULL,
                        number INTEGER NOT NULL,
                        dueDate TEXT NOT NULL,
                        principalMinor INTEGER NOT NULL,
                        interestMinor INTEGER NOT NULL,
                        totalMinor INTEGER NOT NULL,
                        remainingPrincipalMinor INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        PRIMARY KEY(loanId, number)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO loan_installments (
                        loanId, number, dueDate, principalMinor, interestMinor,
                        totalMinor, remainingPrincipalMinor, status
                    ) VALUES ('legacy-loan', 1, '2026-11-01', 9000, 1000, 10000, 0, 'PENDING')
                """.trimIndent())
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(callback)
                .build()
        )

        try {
            val db = helper.writableDatabase
            MIGRATION_4_5.migrate(db)

            db.query("""
                SELECT principalMinor, paidLateFeeMinor, paidChargesMinor,
                       paidInterestMinor, paidPrincipalMinor
                FROM loan_installments
                WHERE loanId = 'legacy-loan' AND number = 1
            """.trimIndent()).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(9000L, cursor.getLong(0))
                assertEquals(0L, cursor.getLong(1))
                assertEquals(0L, cursor.getLong(2))
                assertEquals(0L, cursor.getLong(3))
                assertEquals(0L, cursor.getLong(4))
            }

            db.query("PRAGMA table_info(payment_allocations)").use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns.add(cursor.getString(1))
                assertEquals(
                    setOf(
                        "operationId", "loanId", "installmentNumber",
                        "lateFeePaidMinor", "chargesPaidMinor",
                        "interestPaidMinor", "principalPaidMinor"
                    ),
                    columns
                )
            }

            db.execSQL("INSERT INTO payments(operationId) VALUES ('payment-1')")
            db.execSQL("""
                INSERT INTO payment_allocations (
                    operationId, loanId, installmentNumber, lateFeePaidMinor,
                    chargesPaidMinor, interestPaidMinor, principalPaidMinor
                ) VALUES ('payment-1', 'legacy-loan', 1, 0, 0, 1000, 9000)
            """.trimIndent())
            db.query("SELECT COUNT(*) FROM payment_allocations").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
            }
        } finally {
            helper.close()
        }
    }
}
