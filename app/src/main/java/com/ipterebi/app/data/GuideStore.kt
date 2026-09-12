package com.ipterebi.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ipterebi.core.XmltvProgramme

/**
 * The full guide, kept on the device, per line.
 *
 * `xmltv.php` on the first real line was 76 MB and took the TV box 52 seconds
 * to read, so it is fetched in the background at most twice a day and kept
 * here; screens only ever query it. Only the line's own channels are stored —
 * that line's guide covered 8,350 channels and it carried 1,569.
 *
 * A refresh writes a whole new generation beside the one in use and switches
 * over only when it is complete, in small batches rather than one long
 * transaction, so readers never see half a guide and a download that fails
 * part-way leaves the old guide as it was.
 */
class GuideStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "guide.db", null, 1) {

    init {
        // Readers keep reading the old generation while a refresh writes.
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE programme (line TEXT NOT NULL, gen INTEGER NOT NULL, channel TEXT NOT NULL, " +
                "start INTEGER NOT NULL, stop INTEGER NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX programme_by_channel ON programme (line, gen, channel, start)")
        db.execSQL("CREATE TABLE guide (line TEXT PRIMARY KEY, gen INTEGER NOT NULL, fetched INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** When [line]'s guide was last fetched, in epoch millis; null when never. */
    fun fetchedAt(line: String): Long? =
        readableDatabase.rawQuery("SELECT fetched FROM guide WHERE line = ?", arrayOf(line)).use {
            if (it.moveToFirst()) it.getLong(0) else null
        }

    /**
     * Starts writing a new generation of [line]'s guide. Add to it, then
     * [Writer.commit] to put it in use; anything not committed is discarded
     * by the next refresh.
     */
    fun writer(line: String): Writer {
        val current = readableDatabase.rawQuery("SELECT gen FROM guide WHERE line = ?", arrayOf(line)).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
        return Writer(line, current + 1)
    }

    inner class Writer internal constructor(private val line: String, private val gen: Long) {
        private val batch = ArrayList<XmltvProgramme>(BATCH)
        var written = 0
            private set

        fun add(programme: XmltvProgramme) {
            batch += programme
            if (batch.size >= BATCH) flush()
        }

        private fun flush() {
            if (batch.isEmpty()) return
            val db = writableDatabase
            db.beginTransaction()
            try {
                val insert = db.compileStatement(
                    "INSERT INTO programme (line, gen, channel, start, stop, title, description) VALUES (?, ?, ?, ?, ?, ?, ?)"
                )
                for (p in batch) {
                    insert.clearBindings()
                    insert.bindString(1, line)
                    insert.bindLong(2, gen)
                    insert.bindString(3, p.channel)
                    insert.bindLong(4, p.start)
                    insert.bindLong(5, p.stop)
                    insert.bindString(6, p.title)
                    // A guide's descriptions are most of its size, and a
                    // screen shows a few lines of one at most.
                    insert.bindString(7, p.description.take(MAX_DESCRIPTION))
                    insert.executeInsert()
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            written += batch.size
            batch.clear()
        }

        /** Puts this generation in use and drops every other one for the line. */
        fun commit(fetchedAt: Long) {
            flush()
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.insertWithOnConflict(
                    "guide",
                    null,
                    ContentValues().apply {
                        put("line", line)
                        put("gen", gen)
                        put("fetched", fetchedAt)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                db.delete("programme", "line = ? AND gen != ?", arrayOf(line, gen.toString()))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    /**
     * [channel]'s programmes on [line] that end after [after] (unix seconds),
     * in order, at most [limit]. Empty when the guide does not have it.
     */
    fun programmes(line: String, channel: String, after: Long, limit: Int = 8): List<XmltvProgramme> =
        readableDatabase.rawQuery(
            "SELECT p.start, p.stop, p.title, p.description FROM programme p JOIN guide g " +
                "ON p.line = g.line AND p.gen = g.gen " +
                "WHERE p.line = ? AND p.channel = ? AND p.stop > ? ORDER BY p.start LIMIT ?",
            arrayOf(line, channel, after.toString(), limit.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(XmltvProgramme(channel, c.getLong(0), c.getLong(1), c.getString(2), c.getString(3)))
                }
            }
        }

    /** Forgets [line]'s guide, when it is signed out of. */
    fun clear(line: String) {
        val db = writableDatabase
        db.delete("programme", "line = ?", arrayOf(line))
        db.delete("guide", "line = ?", arrayOf(line))
    }

    private companion object {
        const val BATCH = 2_000
        const val MAX_DESCRIPTION = 400
    }
}
