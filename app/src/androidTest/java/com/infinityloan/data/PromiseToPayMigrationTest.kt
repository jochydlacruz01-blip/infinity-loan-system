package com.infinityloan.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class PromiseToPayMigrationTest {
    @Test
    fun migrationTwelveToThirteenCreatesIndexedPromiseLedgerWithoutChangingExistingData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val callback = object : SupportSQLiteOpenHelper.Callback(12) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE clients (id TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("CREATE TABLE loans (id TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("CREATE TABLE staff_accounts (id TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("INSERT INTO clients(id) VALUES ('client-1')")
                db.execSQL("INSERT INTO loans(id) VALUES ('loan-1')")
                db.execSQL("INSERT INTO staff_accounts(id) VALUES ('collector-1')")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null).callback(callback).build()
        )
        try {
            val db = helper.writableDatabase
            MIGRATION_12_13.migrate(db)
            db.execSQL("""
                INSERT INTO promises_to_pay (
                    promiseId, loanId, clientId, collectorId, promisedAmountMinor,
                    currency, promisedDate, status, note, createdAt, updatedAt
                ) VALUES ('promise-1', 'loan-1', 'client-1', 'collector-1',
                    125000, 'DOP', '2026-11-25', 'OPEN', '', 1, 1)
            """.trimIndent())
            db.query("SELECT promisedAmountMinor, status FROM promises_to_pay WHERE promiseId = 'promise-1'").use {
                assertEquals(true, it.moveToFirst())
                assertEquals(125000L, it.getLong(0))
                assertEquals("OPEN", it.getString(1))
            }
            db.query("PRAGMA index_list(promises_to_pay)").use {
                val names = mutableSetOf<String>()
                while (it.moveToNext()) names.add(it.getString(1))
                assertEquals(true, names.contains("index_promises_to_pay_status"))
                assertEquals(true, names.contains("index_promises_to_pay_promisedDate"))
            }
        } finally {
            helper.close()
        }
    }
}
