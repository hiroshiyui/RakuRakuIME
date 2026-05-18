/*
 * RakuRaku IME - EZ Input Method for Android
 * Copyright (C) 2026  RakuRaku IME Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.ghostsinthelab.app.rakurakuime.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Serialisation for the Backup / Restore manager.
 *
 * **Format:** gzipped UTF-8 JSON with a top-level schema version and
 * `applicationId` guard. Two payload arrays:
 *
 *  - `userPhrases` — every row of the user_phrases table.
 *  - `dictionaryFrequencies` — only the dictionary rows with non-zero
 *    learned `frequency`; the bundled corpus is static and shipped in
 *    the asset DB, so backing it up would just bloat the archive.
 *
 * Restoring on a fresh install: the asset DB seeds the dictionary, then
 * the importer (a) inserts user phrases via `OR IGNORE` and (b) adds the
 * stored frequency on top of whatever the row already has.
 *
 * **Untrusted-input policy.** Users pick the file via SAF, so it can be
 * anything. The parser:
 *  - Caps total uncompressed bytes at [MAX_UNCOMPRESSED_BYTES] so a
 *    crafted gzip bomb can't force the app to allocate without bound.
 *  - Rejects unknown top-level keys (strict decode) — a future schema
 *    must be opt-in via [SCHEMA_VERSION].
 *  - Caps every per-row string at the same limits the CRUD UI enforces
 *    ([MAX_CHARACTER_LEN] / [MAX_KEYSTROKE_LEN]).
 *  - Validates keystroke characters against the EZ root set, the same
 *    validator used by [UserPhraseCsv].
 *  - Surfaces the first validation error to the caller and aborts the
 *    whole import — never applies a partial restore.
 *
 * Kept Android-free so the format and validation rules can be exercised
 * from a JVM unit test.
 */
object BackupArchive {
    const val SCHEMA_VERSION: Int = 1
    const val APPLICATION_ID: String = "org.ghostsinthelab.app.rakurakuime"

    /**
     * Hard cap on uncompressed payload size. 50 MiB is generous for a
     * realistic backup (the entire shipped corpus encodes to <10 MiB
     * uncompressed JSON) while still guarding against a malicious file
     * inflating to gigabytes.
     */
    const val MAX_UNCOMPRESSED_BYTES: Long = 50L * 1024 * 1024

    private const val MAX_CHARACTER_LEN = 100
    private const val MAX_KEYSTROKE_LEN = 200
    private const val MAX_ROWS_PER_TABLE = 1_000_000

    // Recognised top-level keys. Decode is strict: any other key triggers
    // [ParseResult.Error.UnknownField] so a future schema must bump
    // [SCHEMA_VERSION] before the parser tolerates new fields.
    private val ALLOWED_TOP_KEYS = setOf(
        "schema", "applicationId", "createdAt", "userPhrases", "dictionaryFrequencies",
    )
    private val ALLOWED_USER_PHRASE_KEYS = setOf(
        "character", "keystroke", "frequency", "createdAt",
    )
    private val ALLOWED_FREQ_KEYS = setOf("character", "keystroke", "frequency")

    data class UserPhraseRow(
        val character: String,
        val keystroke: String,
        val frequency: Long,
        val createdAt: Long,
    )

    data class FrequencyRow(
        val character: String,
        val keystroke: String,
        val frequency: Int,
    )

    data class Archive(
        val createdAt: Long,
        val userPhrases: List<UserPhraseRow>,
        val dictionaryFrequencies: List<FrequencyRow>,
    )

    sealed class ParseResult {
        data class Ok(val archive: Archive) : ParseResult()
        sealed class Error : ParseResult() {
            /** Stream isn't valid gzip, isn't valid JSON, or exceeded the byte cap. */
            object InvalidFile : Error()
            /** Top-level `schema` field is missing or not the expected integer. */
            data class UnsupportedSchema(val found: Int?) : Error()
            /** `applicationId` doesn't match this build (cross-app archive). */
            data class WrongApplication(val found: String?) : Error()
            /** Strict-decode rejection. */
            data class UnknownField(val path: String) : Error()
            /** Per-row validation failure with a 0-based row index for diagnostics. */
            data class InvalidUserPhrase(val rowIndex: Int) : Error()
            data class InvalidFrequencyRow(val rowIndex: Int) : Error()
            /** Either array exceeded [MAX_ROWS_PER_TABLE]. */
            object TooManyRows : Error()
        }
    }

    /**
     * Serialise [archive] to gzipped JSON. The caller owns [out] and is
     * responsible for closing it; we close the GZIP wrapper to flush the
     * trailer but leave the underlying stream open (matches Android's
     * SAF contract where the platform owns the lifetime).
     */
    fun writeTo(out: OutputStream, archive: Archive) {
        val gzip = GZIPOutputStream(out)
        val writer = OutputStreamWriter(gzip, Charsets.UTF_8)
        val root = JSONObject()
        root.put("schema", SCHEMA_VERSION)
        root.put("applicationId", APPLICATION_ID)
        root.put("createdAt", archive.createdAt)

        val userArr = JSONArray()
        for (row in archive.userPhrases) {
            val obj = JSONObject()
            obj.put("character", row.character)
            obj.put("keystroke", row.keystroke)
            obj.put("frequency", row.frequency)
            obj.put("createdAt", row.createdAt)
            userArr.put(obj)
        }
        root.put("userPhrases", userArr)

        val freqArr = JSONArray()
        for (row in archive.dictionaryFrequencies) {
            val obj = JSONObject()
            obj.put("character", row.character)
            obj.put("keystroke", row.keystroke)
            obj.put("frequency", row.frequency)
            freqArr.put(obj)
        }
        root.put("dictionaryFrequencies", freqArr)

        writer.write(root.toString())
        writer.flush()
        gzip.finish()
        gzip.close()
    }

    /**
     * Parse a gzipped backup from [input], applying the byte cap and
     * validation rules described in the file header. Callers own [input]
     * and the SAF stream lifetime; we don't close it.
     */
    fun parse(input: InputStream, validKeystrokeChars: Set<Char>): ParseResult {
        val text: String = try {
            readGzipCapped(input, MAX_UNCOMPRESSED_BYTES)
        } catch (_: GzipTooLargeException) {
            return ParseResult.Error.InvalidFile
        } catch (_: java.io.IOException) {
            return ParseResult.Error.InvalidFile
        }

        val root: JSONObject = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return ParseResult.Error.InvalidFile
        }

        // Strict decode: every top-level key must be recognised.
        root.keys().forEach { key ->
            if (key !in ALLOWED_TOP_KEYS) {
                return ParseResult.Error.UnknownField(key)
            }
        }

        val schema = root.optInt("schema", -1)
        if (schema != SCHEMA_VERSION) {
            return ParseResult.Error.UnsupportedSchema(if (schema == -1) null else schema)
        }
        val appId = root.optString("applicationId", "")
        if (appId != APPLICATION_ID) {
            return ParseResult.Error.WrongApplication(appId.takeIf { it.isNotEmpty() })
        }
        val createdAt = root.optLong("createdAt", 0L)

        val userArr = root.optJSONArray("userPhrases") ?: JSONArray()
        if (userArr.length() > MAX_ROWS_PER_TABLE) return ParseResult.Error.TooManyRows
        val userPhrases = ArrayList<UserPhraseRow>(userArr.length())
        for (i in 0 until userArr.length()) {
            val obj = userArr.optJSONObject(i)
                ?: return ParseResult.Error.InvalidUserPhrase(i)
            obj.keys().forEach { key ->
                if (key !in ALLOWED_USER_PHRASE_KEYS) {
                    return ParseResult.Error.UnknownField("userPhrases[$i].$key")
                }
            }
            val character = obj.optString("character", "")
            val keystroke = obj.optString("keystroke", "")
            val frequency = obj.optLong("frequency", 0L)
            val rowCreatedAt = obj.optLong("createdAt", 0L)
            if (!isValidPhraseString(character) ||
                !isValidKeystrokeString(keystroke, validKeystrokeChars) ||
                frequency < 0 || frequency > Int.MAX_VALUE.toLong() ||
                rowCreatedAt < 0
            ) {
                return ParseResult.Error.InvalidUserPhrase(i)
            }
            userPhrases.add(UserPhraseRow(character, keystroke, frequency, rowCreatedAt))
        }

        val freqArr = root.optJSONArray("dictionaryFrequencies") ?: JSONArray()
        if (freqArr.length() > MAX_ROWS_PER_TABLE) return ParseResult.Error.TooManyRows
        val freqs = ArrayList<FrequencyRow>(freqArr.length())
        for (i in 0 until freqArr.length()) {
            val obj = freqArr.optJSONObject(i)
                ?: return ParseResult.Error.InvalidFrequencyRow(i)
            obj.keys().forEach { key ->
                if (key !in ALLOWED_FREQ_KEYS) {
                    return ParseResult.Error.UnknownField("dictionaryFrequencies[$i].$key")
                }
            }
            val character = obj.optString("character", "")
            val keystroke = obj.optString("keystroke", "")
            val frequency = obj.optInt("frequency", -1)
            if (!isValidPhraseString(character) ||
                !isValidKeystrokeString(keystroke, validKeystrokeChars) ||
                frequency < 1 // skip 0/missing; only positive bumps are worth storing
            ) {
                return ParseResult.Error.InvalidFrequencyRow(i)
            }
            freqs.add(FrequencyRow(character, keystroke, frequency))
        }

        return ParseResult.Ok(Archive(createdAt, userPhrases, freqs))
    }

    private fun isValidPhraseString(s: String): Boolean {
        if (s.isEmpty() || s.length > MAX_CHARACTER_LEN) return false
        // Reject control characters; allow letters, digits, punctuation, CJK,
        // symbols — anything the user could reasonably have typed.
        return s.none { it.isISOControl() }
    }

    private fun isValidKeystrokeString(s: String, validChars: Set<Char>): Boolean {
        if (s.isEmpty() || s.length > MAX_KEYSTROKE_LEN) return false
        return UserPhraseCsv.isValidKeystroke(s, validChars)
    }

    /**
     * Read [input] as gzip up to [limit] uncompressed bytes; throw
     * [GzipTooLargeException] if the stream would produce more. We never
     * buffer the full archive — we copy into a bounded ByteArrayOutputStream
     * and tip over as soon as one extra byte is decoded past the limit.
     */
    private fun readGzipCapped(input: InputStream, limit: Long): String {
        val gz = GZIPInputStream(input)
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val n = gz.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) throw GzipTooLargeException()
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private class GzipTooLargeException : RuntimeException()
}
