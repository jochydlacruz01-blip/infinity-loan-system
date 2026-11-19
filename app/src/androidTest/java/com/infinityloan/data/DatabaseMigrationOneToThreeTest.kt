package com.infinityloan.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseMigrationOneToThreeTest {

    @Test
    fun migrationsOneToThreePreserveCreditMovementsAndAddLoanForeignKey() {
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
                        id TEXT NOT NULL PRIMARY KEY
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO clients (
                        id, fullName, documentNumber, phone, address, createdAt
                    ) VALUES ('client-1', 'Cliente legado', 'DOC-1', '', '', 1)
                """.trimIndent())
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
            InfinityDatabase.MIGRATION_1_2.migrate(db)
            db.execSQL("""
                INSERT INTO customer_credit_movements (
                    operationId, clientId, loanId, currency, amountMinor, reference, createdAt
                ) VALUES ('credit-1', 'client-1', 'loan-1', 'DOP', 2500, 'Saldo legado', 2)
            """.trimIndent())

            InfinityDatabase.MIGRATION_2_3.migrate(db)

            db.query("""
                SELECT operationId, clientId, loanId, currency, amountMinor, reference, createdAt
                FROM customer_credit_movements
                WHERE operationId = 'credit-1'
            """.trimIndent()).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("credit-1", cursor.getString(0))
                assertEquals("client-1", cursor.getString(1))
                assertEquals("loan-1", cursor.getString(2))
                assertEquals("DOP", cursor.getString(3))
                assertEquals(2500L, cursor.getLong(4))
                assertEquals("Saldo legado", cursor.getString(5))
                assertEquals(2L, cursor.getLong(6))
            }

            db.query("PRAGMA foreign_key_list(customer_credit_movements)").use { cursor ->
                val referencedTables = mutableSetOf<String>()
                while (cursor.moveToNext()) referencedTables.add(cursor.getString(2))
                assertEquals(setOf("clients", "loans"), referencedTables)
            }

            db.query("PRAGMA index_list(customer_credit_movements)").use { cursor ->
                val indexes = mutableSetOf<String>()
                while (cursor.moveToNext()) indexes.add(cursor.getString(1))
                assertTrue(indexes.contains("index_customer_credit_movements_clientId_currency"))
                assertTrue(indexes.contains("index_customer_credit_movements_loanId"))
            }
        } finally {
            helper.close()
        }
    }
}
