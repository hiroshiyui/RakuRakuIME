/*
 * RakuRaku IME - EZ Input Method for Android
 * Copyright (C) 2026  RakuRaku IME Contributors
 *
 * Licensed under GPL-3.0-or-later. See the project LICENSE file for the
 * full text.
 */

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Builds `app/src/main/assets/databases/ime_database.db` from scratch:
 *
 *   ezbig.utf-8.cin (keystroke ↔ character pairs)
 * + 85rest01.csv     (字頻 → character_weight)
 * + 85rest02.csv     (詞頻 → phrase_weight)
 *   ───────────────────────────────────────────────
 * = Room v3-compatible SQLite database
 *
 * This object is invoked from the `:app:buildImeDb` Gradle task. The task
 * is **not** wired into the regular assemble pipeline — the produced
 * `.db` is committed and only refreshed on demand.
 *
 * Schema and trigger DDL match what Room's compiler generates for the
 * `@Database(version = 3)` annotation in `data/ImeDatabase.kt`. The
 * `room_master_table.identity_hash` is read from
 * `app/schemas/.../3.json` so a Room version bump is reflected here
 * without touching this file.
 */
object ImeDbBuilder {
    /** Numerator of the reciprocal weight formula. Mirror of
     *  `data.FrequencyCsv.SEED_NUMERATOR`. */
    const val SEED_NUMERATOR = 10_000

    /** Sanity floor — fewer rows than this implies a truncated CIN. */
    private const val MIN_EXPECTED_ENTRIES = 90_000

    /**
     * Build the database file. Overwrites [output] atomically.
     */
    fun build(
        cin: File,
        charCsv: File,
        phraseCsv: File,
        schemaJson: File,
        output: File,
    ) {
        require(cin.exists()) { "CIN missing: $cin" }
        require(charCsv.exists()) { "char CSV missing: $charCsv" }
        require(phraseCsv.exists()) { "phrase CSV missing: $phraseCsv" }
        require(schemaJson.exists()) { "Room v3 schema missing: $schemaJson" }

        val identityHash = readIdentityHash(schemaJson)
        val charWeights = loadWeights(charCsv)
        val phraseWeights = loadWeights(phraseCsv)
        val entries = parseCin(cin)
        check(entries.size >= MIN_EXPECTED_ENTRIES) {
            "CIN parsed only ${entries.size} entries (expected ≥ $MIN_EXPECTED_ENTRIES); " +
                "${cin.name} may be truncated or corrupt."
        }

        // Atomic-rename trick: build alongside, swap at the end.
        output.parentFile.mkdirs()
        val tmp = File(output.parentFile, output.name + ".tmp")
        if (tmp.exists()) tmp.delete()

        // Force-load the JDBC driver so it's discoverable inside Gradle's
        // build classloader (where ServiceLoader can be flaky).
        Class.forName("org.sqlite.JDBC")

        DriverManager.getConnection("jdbc:sqlite:${tmp.absolutePath}").use { conn ->
            conn.autoCommit = false
            createSchemaWithoutFtsTriggers(conn)
            insertRows(conn, entries, charWeights, phraseWeights)
            populateFts(conn)
            createFtsTriggers(conn)
            insertMetadata(conn, identityHash)
            setUserVersion(conn, 3)
            conn.commit()
            // VACUUM must run outside a transaction.
            conn.autoCommit = true
            conn.createStatement().use { it.execute("VACUUM") }
        }

        if (output.exists()) output.delete()
        check(tmp.renameTo(output)) { "failed to swap $tmp -> $output" }
    }

    // ---------------------------------------------------------------------
    // Inputs
    // ---------------------------------------------------------------------

    /** Extract the v3 identityHash from a Room-exported schema JSON. */
    private fun readIdentityHash(schemaJson: File): String {
        val regex = Regex(""""identityHash"\s*:\s*"([0-9a-f]+)"""")
        val text = schemaJson.readText(Charsets.UTF_8)
        return regex.find(text)?.groupValues?.get(1)
            ?: error("identityHash not found in ${schemaJson.name}")
    }

    /**
     * Parse a MOE 字頻／詞頻 CSV (UTF-8, comma-separated) into
     * `key → weight` where weight = SEED_NUMERATOR / rank. Stops at the
     * first zero-weight row (CSVs are rank-sorted).
     */
    fun loadWeights(csv: File): Map<String, Int> {
        val out = LinkedHashMap<String, Int>(8192)
        csv.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                val cols = line.split(',')
                if (cols.size < 2) continue
                val rank = cols[0].trim().toIntOrNull() ?: continue
                val key = cols[1].trim()
                if (key.isEmpty()) continue
                val weight = if (rank > 0) SEED_NUMERATOR / rank else 0
                if (weight == 0) break
                out.putIfAbsent(key, weight)
            }
        }
        return out
    }

    private data class CinEntry(val keystroke: String, val character: String)

    /**
     * Parse `ezbig.utf-8.cin` into (keystroke, character) pairs. Mirrors
     * the runtime parser in `data/CinParser.kt::parseAndPopulate`.
     */
    private fun parseCin(cin: File): List<CinEntry> {
        val out = ArrayList<CinEntry>(150_000)
        var inKeyname = false
        cin.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                if (line == "%keyname begin") {
                    inKeyname = true
                    continue
                }
                if (line == "%keyname end") {
                    inKeyname = false
                    continue
                }
                if (inKeyname || line.startsWith("%")) continue
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2) out += CinEntry(parts[0], parts[1])
            }
        }
        return out
    }

    // ---------------------------------------------------------------------
    // Schema (matches what Room generates for v3)
    // ---------------------------------------------------------------------

    private fun createSchemaWithoutFtsTriggers(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute(
                "CREATE TABLE `dictionary` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                    "`keystroke` TEXT NOT NULL," +
                    "`character` TEXT NOT NULL," +
                    "`frequency` INTEGER NOT NULL," +
                    "`character_weight` INTEGER NOT NULL DEFAULT 0," +
                    "`phrase_weight` INTEGER NOT NULL DEFAULT 0)"
            )
            st.execute(
                "CREATE INDEX `index_dictionary_keystroke` ON `dictionary` (`keystroke`)"
            )
            st.execute(
                "CREATE VIRTUAL TABLE `dictionary_fts` USING FTS4(" +
                    "`keystroke` TEXT NOT NULL, `character` TEXT NOT NULL, content=`dictionary`)"
            )
        }
    }

    /**
     * Recreate the four FTS sync triggers that Room emits for an FTS4
     * entity with `contentEntity = DictionaryEntry`. Names are reserved
     * by Room — keep them byte-identical.
     */
    private fun createFtsTriggers(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute(
                "CREATE TRIGGER room_fts_content_sync_dictionary_fts_BEFORE_UPDATE " +
                    "BEFORE UPDATE ON `dictionary` BEGIN " +
                    "DELETE FROM `dictionary_fts` WHERE `docid`=OLD.`rowid`; END"
            )
            st.execute(
                "CREATE TRIGGER room_fts_content_sync_dictionary_fts_BEFORE_DELETE " +
                    "BEFORE DELETE ON `dictionary` BEGIN " +
                    "DELETE FROM `dictionary_fts` WHERE `docid`=OLD.`rowid`; END"
            )
            st.execute(
                "CREATE TRIGGER room_fts_content_sync_dictionary_fts_AFTER_UPDATE " +
                    "AFTER UPDATE ON `dictionary` BEGIN " +
                    "INSERT INTO `dictionary_fts`(`docid`, `keystroke`, `character`) " +
                    "VALUES (NEW.`rowid`, NEW.`keystroke`, NEW.`character`); END"
            )
            st.execute(
                "CREATE TRIGGER room_fts_content_sync_dictionary_fts_AFTER_INSERT " +
                    "AFTER INSERT ON `dictionary` BEGIN " +
                    "INSERT INTO `dictionary_fts`(`docid`, `keystroke`, `character`) " +
                    "VALUES (NEW.`rowid`, NEW.`keystroke`, NEW.`character`); END"
            )
        }
    }

    // ---------------------------------------------------------------------
    // Data
    // ---------------------------------------------------------------------

    private fun insertRows(
        conn: Connection,
        entries: List<CinEntry>,
        charWeights: Map<String, Int>,
        phraseWeights: Map<String, Int>,
    ) {
        val sql = "INSERT INTO `dictionary` " +
            "(`keystroke`, `character`, `frequency`, `character_weight`, `phrase_weight`) " +
            "VALUES (?, ?, 0, ?, ?)"
        conn.prepareStatement(sql).use { ps ->
            var batched = 0
            for (e in entries) {
                val isSingleChar = e.character.codePointCount(0, e.character.length) == 1
                val cw = if (isSingleChar) charWeights[e.character] ?: 0 else 0
                val pw = if (!isSingleChar) phraseWeights[e.character] ?: 0 else 0
                ps.setString(1, e.keystroke)
                ps.setString(2, e.character)
                ps.setInt(3, cw)
                ps.setInt(4, pw)
                ps.addBatch()
                batched++
                if (batched >= 5000) {
                    ps.executeBatch()
                    batched = 0
                }
            }
            if (batched > 0) ps.executeBatch()
        }
    }

    /**
     * Populate the FTS shadow table in one bulk INSERT. Done after row
     * inserts and before triggers are installed so the heavy work
     * happens once, not per-row.
     */
    private fun populateFts(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute(
                "INSERT INTO `dictionary_fts`(`docid`, `keystroke`, `character`) " +
                    "SELECT `id`, `keystroke`, `character` FROM `dictionary`"
            )
        }
    }

    private fun insertMetadata(conn: Connection, identityHash: String) {
        conn.createStatement().use { st ->
            // Room's identity-check table.
            st.execute(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            // Android's per-DB locale table; createFromAsset expects it.
            st.execute(
                "CREATE TABLE IF NOT EXISTS android_metadata (locale TEXT DEFAULT 'en_US')"
            )
        }
        conn.prepareStatement(
            "INSERT INTO room_master_table (id, identity_hash) VALUES (42, ?)"
        ).use { ps ->
            ps.setString(1, identityHash)
            ps.executeUpdate()
        }
        conn.prepareStatement(
            "INSERT INTO android_metadata (locale) VALUES (?)"
        ).use { ps ->
            ps.setString(1, "en_US")
            ps.executeUpdate()
        }
    }

    private fun setUserVersion(conn: Connection, version: Int) {
        conn.createStatement().use { st ->
            // PRAGMA user_version doesn't take parameters; safe to inline an Int.
            st.execute("PRAGMA user_version = $version")
        }
    }
}
