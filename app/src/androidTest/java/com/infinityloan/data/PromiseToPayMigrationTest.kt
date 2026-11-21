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

    @Test
    fun migrationFourteenToFifteenCreatesIndexedPaymentEvidenceLedger() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val callback = object : SupportSQLiteOpenHelper.Callback(14) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE promises_to_pay (promiseId TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("CREATE TABLE payments (operationId TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("INSERT INTO promises_to_pay(promiseId) VALUES ('promise-15')")
                db.execSQL("INSERT INTO payments(operationId) VALUES ('payment-15')")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null).callback(callback).build()
        )
        try {
            val db = helper.writableDatabase
            MIGRATION_14_15.migrate(db)
            db.execSQL("""
                INSERT INTO promise_payment_links (
                    promiseId, operationId, amountMinor, linkedByStaffId, createdAt
                ) VALUES ('promise-15', 'payment-15', 5000, 'collector-1', 100)
            """.trimIndent())
            db.query("SELECT amountMinor FROM promise_payment_links WHERE promiseId = 'promise-15'").use {
                assertEquals(true, it.moveToFirst())
                assertEquals(5000L, it.getLong(0))
            }
            db.query("PRAGMA index_list(promise_payment_links)").use {
                val indexes = mutableSetOf<String>()
                while (it.moveToNext()) indexes.add(it.getString(1))
                assertEquals(true, "index_promise_payment_links_promiseId" in indexes)
                assertEquals(true, "index_promise_payment_links_operationId" in indexes)
            }
            db.query("PRAGMA foreign_key_list(promise_payment_links)").use {
                val keys = mutableSetOf<String>()
                while (it.moveToNext()) keys.add("${it.getString(2)}:${it.getString(3)}:${it.getString(4)}:${it.getString(6)}")
                assertEquals(true, "promises_to_pay:promiseId:promiseId:RESTRICT" in keys)
                assertEquals(true, "payments:operationId:operationId:RESTRICT" in keys)
            }
        } finally {
            helper.close()
        }
    }

}
