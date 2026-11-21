package com.infinityloan.data

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionVisitMigrationTest {
    @Test
    fun migrationThirteenToFourteenCreatesIndexedVisitTableWithoutChangingLegacyRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val callback = object : SupportSQLiteOpenHelper.Callback(13) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE clients (id TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("CREATE TABLE loans (id TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("CREATE TABLE staff_accounts (id TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("INSERT INTO clients(id) VALUES ('client-1')")
                db.execSQL("INSERT INTO loans(id) VALUES ('loan-1')")
                db.execSQL("INSERT INTO staff_accounts(id) VALUES ('collector-1')")
                db.execSQL("CREATE TABLE legacy_marker (id TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("INSERT INTO legacy_marker(id) VALUES ('preserved')")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null).callback(callback).build()
        )
        try {
            val db = helper.writableDatabase
            MIGRATION_13_14.migrate(db)
            db.execSQL("""
                INSERT INTO collection_visits (
                    visitId, loanId, clientId, collectorId, plannedDate,
                    status, outcomeNote, createdAt, updatedAt
                ) VALUES ('visit-1', 'loan-1', 'client-1', 'collector-1',
                    '2026-11-25', 'SCHEDULED', '', 1, 1)
            """.trimIndent())
            db.query("SELECT status, plannedDate FROM collection_visits WHERE visitId = 'visit-1'").use {
                assertTrue(it.moveToFirst())
                assertEquals("SCHEDULED", it.getString(0))
                assertEquals("2026-11-25", it.getString(1))
            }
            db.query("SELECT id FROM legacy_marker").use {
                assertTrue(it.moveToFirst())
                assertEquals("preserved", it.getString(0))
            }
            db.query("PRAGMA index_list(collection_visits)").use {
                val names = mutableSetOf<String>()
                while (it.moveToNext()) names.add(it.getString(1))
                assertTrue(names.contains("index_collection_visits_status"))
                assertTrue(names.contains("index_collection_visits_plannedDate"))
                assertTrue(names.contains("index_collection_visits_collectorId"))
            }
        } finally {
            helper.close()
        }
    }
}
