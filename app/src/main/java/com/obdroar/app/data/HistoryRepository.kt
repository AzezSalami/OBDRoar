package com.obdroar.app.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.obdroar.app.obd.Pids
import com.obdroar.app.obd.Sensors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One saved reading. [pid] is an OBD PID or SensorInfoDb.BATTERY. */
data class Sample(val timeMs: Long, val pid: Int, val value: Double)

/** A drive / connection session as shown in the History list. */
data class SessionSummary(
    val id: Long,
    val startMs: Long,
    val endMs: Long?,
    val device: String?,
    val vin: String?,
    val demo: Boolean,
    val samples: Int,
    val maxRpm: Double?,
    val maxSpeed: Double?,
    val dtcCount: Int,
)

data class Series(val pid: Int, val times: LongArray, val values: FloatArray)

data class LoggedDtc(val code: String, val kind: String, val timeMs: Long)

data class HistoryStats(val sessions: Int, val samples: Long, val bytes: Long)

private class HistoryDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, 1) {
    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, start_ms INTEGER NOT NULL, " +
                "end_ms INTEGER, device TEXT, vin TEXT, demo INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE samples (session_id INTEGER NOT NULL, t_ms INTEGER NOT NULL, " +
                "pid INTEGER NOT NULL, value REAL NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_samples ON samples(session_id, pid, t_ms)")
        db.execSQL(
            "CREATE TABLE dtc_log (session_id INTEGER NOT NULL, t_ms INTEGER NOT NULL, code TEXT NOT NULL, " +
                "kind TEXT NOT NULL, UNIQUE(session_id, code, kind))"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    companion object {
        const val DB_NAME = "history.db"
    }
}

/**
 * Stores every connection as a session with its sensor readings and the trouble codes seen.
 * All calls run on the IO dispatcher. Uses Android's built-in SQLite – no extra libraries.
 */
class HistoryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val helper = HistoryDb(appContext)

    /** Outlives the ViewModel so the last readings are still saved when the app closes. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Close sessions left open by a crash or a killed app, and drop empty ones.
        scope.launch {
            runCatching {
                val db = helper.writableDatabase
                db.execSQL(
                    "UPDATE sessions SET end_ms = COALESCE((SELECT MAX(t_ms) FROM samples WHERE session_id = sessions.id), start_ms) " +
                        "WHERE end_ms IS NULL"
                )
                db.execSQL("DELETE FROM sessions WHERE id NOT IN (SELECT DISTINCT session_id FROM samples)")
            }
        }
    }

    suspend fun startSession(device: String?, demo: Boolean): Long = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.compileStatement("INSERT INTO sessions (start_ms, device, demo) VALUES (?, ?, ?)").use { st ->
            st.bindLong(1, System.currentTimeMillis())
            if (device != null) st.bindString(2, device) else st.bindNull(2)
            st.bindLong(3, if (demo) 1 else 0)
            st.executeInsert()
        }
    }

    suspend fun setVin(sessionId: Long, vin: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.execSQL("UPDATE sessions SET vin = ? WHERE id = ?", arrayOf<Any>(vin, sessionId))
    }

    suspend fun addSamples(sessionId: Long, samples: List<Sample>) = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) return@withContext
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.compileStatement("INSERT INTO samples (session_id, t_ms, pid, value) VALUES (?, ?, ?, ?)").use { st ->
                for (s in samples) {
                    st.bindLong(1, sessionId)
                    st.bindLong(2, s.timeMs)
                    st.bindLong(3, s.pid.toLong())
                    st.bindDouble(4, s.value)
                    st.executeInsert()
                    st.clearBindings()
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** kind = "stored", "pending" or "permanent". Each code is logged once per session and kind. */
    suspend fun addDtcs(sessionId: Long, codes: List<Pair<String, String>>) = withContext(Dispatchers.IO) {
        if (codes.isEmpty()) return@withContext
        val db = helper.writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            db.compileStatement("INSERT OR IGNORE INTO dtc_log (session_id, t_ms, code, kind) VALUES (?, ?, ?, ?)").use { st ->
                for ((code, kind) in codes) {
                    st.bindLong(1, sessionId)
                    st.bindLong(2, now)
                    st.bindString(3, code)
                    st.bindString(4, kind)
                    st.executeInsert()
                    st.clearBindings()
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Saves the last readings and closes the session, even if the caller is going away. */
    fun finishAsync(sessionId: Long, lastSamples: List<Sample>, onDone: () -> Unit) {
        scope.launch {
            runCatching {
                addSamples(sessionId, lastSamples)
                val db = helper.writableDatabase
                db.execSQL(
                    "UPDATE sessions SET end_ms = ? WHERE id = ?",
                    arrayOf<Any>(System.currentTimeMillis(), sessionId)
                )
                db.execSQL(
                    "DELETE FROM sessions WHERE id = ? AND NOT EXISTS (SELECT 1 FROM samples WHERE session_id = ?)",
                    arrayOf<Any>(sessionId, sessionId)
                )
            }
            onDone()
        }
    }

    // ------------------------------------------------------------------ reading

    private val summarySql =
        "SELECT s.id, s.start_ms, s.end_ms, s.device, s.vin, s.demo, " +
            "(SELECT COUNT(*) FROM samples WHERE session_id = s.id), " +
            "(SELECT MAX(value) FROM samples WHERE session_id = s.id AND pid = ${Pids.RPM}), " +
            "(SELECT MAX(value) FROM samples WHERE session_id = s.id AND pid = ${Pids.SPEED}), " +
            "(SELECT COUNT(DISTINCT code) FROM dtc_log WHERE session_id = s.id) " +
            "FROM sessions s"

    private fun Cursor.toSummary() = SessionSummary(
        id = getLong(0),
        startMs = getLong(1),
        endMs = if (isNull(2)) null else getLong(2),
        device = if (isNull(3)) null else getString(3),
        vin = if (isNull(4)) null else getString(4),
        demo = getInt(5) != 0,
        samples = getInt(6),
        maxRpm = if (isNull(7)) null else getDouble(7),
        maxSpeed = if (isNull(8)) null else getDouble(8),
        dtcCount = getInt(9),
    )

    suspend fun sessions(): List<SessionSummary> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("$summarySql ORDER BY s.start_ms DESC", null).use { c ->
            val out = ArrayList<SessionSummary>(c.count)
            while (c.moveToNext()) out += c.toSummary()
            out
        }
    }

    suspend fun session(id: Long): SessionSummary? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("$summarySql WHERE s.id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.toSummary() else null
        }
    }

    /** Sensors that have data in this session, most interesting first. */
    suspend fun pids(sessionId: Long): List<Int> = withContext(Dispatchers.IO) {
        val found = helper.readableDatabase.rawQuery(
            "SELECT DISTINCT pid FROM samples WHERE session_id = ?", arrayOf(sessionId.toString())
        ).use { c ->
            val out = ArrayList<Int>()
            while (c.moveToNext()) out += c.getInt(0)
            out
        }
        val order = Pids.all.map { it.code }
        found.sortedBy { code -> order.indexOf(code).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    suspend fun series(sessionId: Long, pid: Int): Series = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT t_ms, value FROM samples WHERE session_id = ? AND pid = ? ORDER BY t_ms",
            arrayOf(sessionId.toString(), pid.toString())
        ).use { c ->
            val times = LongArray(c.count)
            val values = FloatArray(c.count)
            var i = 0
            while (c.moveToNext()) {
                times[i] = c.getLong(0); values[i] = c.getDouble(1).toFloat(); i++
            }
            Series(pid, times, values)
        }
    }

    suspend fun dtcs(sessionId: Long): List<LoggedDtc> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT code, kind, t_ms FROM dtc_log WHERE session_id = ? ORDER BY t_ms, code",
            arrayOf(sessionId.toString())
        ).use { c ->
            val out = ArrayList<LoggedDtc>()
            while (c.moveToNext()) out += LoggedDtc(c.getString(0), c.getString(1), c.getLong(2))
            out
        }
    }

    suspend fun stats(): HistoryStats = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        val sessions = db.rawQuery("SELECT COUNT(*) FROM sessions", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val samples = db.rawQuery("SELECT COUNT(*) FROM samples", null).use { if (it.moveToFirst()) it.getLong(0) else 0L }
        val path = appContext.getDatabasePath(HistoryDb.DB_NAME)
        val bytes = listOf(path, File(path.path + "-wal")).sumOf { if (it.exists()) it.length() else 0L }
        HistoryStats(sessions, samples, bytes)
    }

    // ------------------------------------------------------------------ deleting

    suspend fun deleteSession(id: Long) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val args = arrayOf<Any>(id)
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM samples WHERE session_id = ?", args)
            db.execSQL("DELETE FROM dtc_log WHERE session_id = ?", args)
            db.execSQL("DELETE FROM sessions WHERE id = ?", args)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Deletes everything except [keepSessionId] (the session being recorded right now). */
    suspend fun deleteAll(keepSessionId: Long?) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val keep = arrayOf<Any>(keepSessionId ?: -1L)
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM samples WHERE session_id != ?", keep)
            db.execSQL("DELETE FROM dtc_log WHERE session_id != ?", keep)
            db.execSQL("DELETE FROM sessions WHERE id != ?", keep)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        runCatching { db.execSQL("VACUUM") } // give the space back to the phone
    }

    // ------------------------------------------------------------------ export

    /**
     * Writes one row per second with a column per sensor, ready for Excel / Google Sheets.
     * Returns the file in the app cache (shared through the FileProvider).
     */
    suspend fun exportCsv(sessionId: Long): File = withContext(Dispatchers.IO) {
        val pids = pids(sessionId)
        val dir = File(appContext.cacheDir, "exports").apply { mkdirs() }
        val start = session(sessionId)?.startMs ?: 0L
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(start))
        val file = File(dir, "OBDRoar_$stamp.csv")
        val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val column = pids.withIndex().associate { it.value to it.index }

        file.bufferedWriter().use { w ->
            w.write("time,elapsed_s")
            for (p in pids) {
                val unit = Sensors.unit(p)
                w.write(",\"" + Sensors.name(p).replace("\"", "'") + (if (unit.isNotEmpty()) " ($unit)" else "") + "\"")
            }
            w.write("\n")

            helper.readableDatabase.rawQuery(
                "SELECT t_ms, pid, value FROM samples WHERE session_id = ? ORDER BY t_ms",
                arrayOf(sessionId.toString())
            ).use { c ->
                var currentSecond = Long.MIN_VALUE
                val row = arrayOfNulls<String>(pids.size)
                fun flushRow() {
                    if (currentSecond == Long.MIN_VALUE) return
                    w.write(timeFmt.format(Date(currentSecond * 1000)))
                    w.write("," + (currentSecond - start / 1000))
                    for (cell in row) w.write("," + (cell ?: ""))
                    w.write("\n")
                    row.fill(null)
                }
                while (c.moveToNext()) {
                    val sec = c.getLong(0) / 1000
                    if (sec != currentSecond) { flushRow(); currentSecond = sec }
                    val idx = column[c.getInt(1)] ?: continue
                    row[idx] = String.format(Locale.US, "%.${Sensors.decimals(c.getInt(1)).coerceAtLeast(1)}f", c.getDouble(2))
                }
                flushRow()
            }
        }
        file
    }
}
