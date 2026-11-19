package com.infinityloan.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseMigrationThreeToFourTest {

    @Test
    fun migrationThreeToFourPreservesLoansAndCreatesMonthlySchedules() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val callback = object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE loans (
                        id TEXT NOT NULL PRIMARY KEY,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL(
                    "INSERT INTO loans(id, createdAt) VALUES ('legacy-loan', 0)"
                )
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
            InfinityDatabase.MIGRATION_3_4.migrate(db)

            db.query("""
                SELECT id, frequency, firstDueDate
                FROM loans
                WHERE id = 'legacy-loan'
            """.trimIndent()).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("legacy-loan", cursor.getString(0))
                assertEquals("MONTHLY", cursor.getString(1))
                assertEquals("1970-01-31", cursor.getString(2))
            }

            db.query("PRAGMA table_info(loan_installments)").use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns.add(cursor.getString(1))
                assertEquals(
                    setOf(
                        "loanId", "number", "dueDate", "principalMinor",
                        "interestMinor", "totalMinor", "remainingPrincipalMinor", "status"
                    ),
                    columns
                )
            }

            db.query("PRAGMA index_list(loan_installments)").use { cursor ->
                val indexes = mutableSetOf<String>()
                while (cursor.moveToNext()) indexes.add(cursor.getString(1))
                assertTrue("Missing loanId index", indexes.contains("index_loan_installments_loanId"))
                assertTrue("Missing dueDate index", indexes.contains("index_loan_installments_dueDate"))
                assertTrue("Missing status index", indexes.contains("index_loan_installments_status"))
            }
        } finally {
            helper.close()
        }
    }
}
