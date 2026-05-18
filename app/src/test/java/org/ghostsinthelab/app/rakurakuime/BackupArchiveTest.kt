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

package org.ghostsinthelab.app.rakurakuime

import org.ghostsinthelab.app.rakurakuime.data.BackupArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * JVM coverage for [BackupArchive]. The end-to-end SAF integration is
 * inherently device-bound; here we pin the format and the validation rules
 * so a future schema bump or a regression in the strict-decode path is
 * caught at unit-test speed.
 */
class BackupArchiveTest {

    /** Stand-in EZ root set — matches the canonical alphabetic + symbol keys. */
    private val validChars: Set<Char> =
        (('a'..'z') + ('0'..'9') + listOf(',', '.', '/', '`', '\'', '-', '=', ';', '[', ']'))
            .toSet()

    private fun gzip(json: String): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return ByteArrayInputStream(out.toByteArray())
    }

    @Test
    fun writeThenParse_roundTripPreservesAllRows() {
        val original = BackupArchive.Archive(
            createdAt = 1_700_000_000_000L,
            userPhrases = listOf(
                BackupArchive.UserPhraseRow("輕鬆", "2mm/", frequency = 7L, createdAt = 12345L),
                BackupArchive.UserPhraseRow("信件", "o4oj", frequency = 0L, createdAt = 99L),
            ),
            dictionaryFrequencies = listOf(
                BackupArchive.FrequencyRow("這", "z4", frequency = 5),
                BackupArchive.FrequencyRow("姆", "v=", frequency = 1),
            ),
        )
        val out = ByteArrayOutputStream()
        BackupArchive.writeTo(out, original)

        val parsed = BackupArchive.parse(ByteArrayInputStream(out.toByteArray()), validChars)
        require(parsed is BackupArchive.ParseResult.Ok) { "expected Ok, got $parsed" }
        assertEquals(original, parsed.archive)
    }

    @Test
    fun parse_emptyArchive_isOkWithEmptyArrays() {
        val empty = BackupArchive.Archive(
            createdAt = 0L,
            userPhrases = emptyList(),
            dictionaryFrequencies = emptyList(),
        )
        val out = ByteArrayOutputStream()
        BackupArchive.writeTo(out, empty)
        val parsed = BackupArchive.parse(ByteArrayInputStream(out.toByteArray()), validChars)
        require(parsed is BackupArchive.ParseResult.Ok)
        assertTrue(parsed.archive.userPhrases.isEmpty())
        assertTrue(parsed.archive.dictionaryFrequencies.isEmpty())
    }

    @Test
    fun parse_unsupportedSchema_isUnsupportedSchemaError() {
        val json = """{"schema":99,"applicationId":"${BackupArchive.APPLICATION_ID}","userPhrases":[],"dictionaryFrequencies":[]}"""
        val result = BackupArchive.parse(gzip(json), validChars)
        assertEquals(BackupArchive.ParseResult.Error.UnsupportedSchema(99), result)
    }

    @Test
    fun parse_missingSchema_isUnsupportedSchemaWithNull() {
        val json = """{"applicationId":"${BackupArchive.APPLICATION_ID}","userPhrases":[],"dictionaryFrequencies":[]}"""
        val result = BackupArchive.parse(gzip(json), validChars)
        assertEquals(BackupArchive.ParseResult.Error.UnsupportedSchema(null), result)
    }

    @Test
    fun parse_wrongApplicationId_isWrongApplicationError() {
        val json = """{"schema":1,"applicationId":"com.evil.app","userPhrases":[],"dictionaryFrequencies":[]}"""
        val result = BackupArchive.parse(gzip(json), validChars)
        assertEquals(
            BackupArchive.ParseResult.Error.WrongApplication("com.evil.app"),
            result,
        )
    }

    @Test
    fun parse_unknownTopLevelField_isStrictDecodeError() {
        val json = """{"schema":1,"applicationId":"${BackupArchive.APPLICATION_ID}","mystery":42,"userPhrases":[],"dictionaryFrequencies":[]}"""
        val result = BackupArchive.parse(gzip(json), validChars)
        assertEquals(BackupArchive.ParseResult.Error.UnknownField("mystery"), result)
    }

    @Test
    fun parse_unknownNestedField_isStrictDecodeError() {
        val json = """{"schema":1,"applicationId":"${BackupArchive.APPLICATION_ID}","userPhrases":[{"character":"x","keystroke":"a","extra":1}],"dictionaryFrequencies":[]}"""
        val result = BackupArchive.parse(gzip(json), validChars)
        assertEquals(
            BackupArchive.ParseResult.Error.UnknownField("userPhrases[0].extra"),
            result,
        )
    }

    @Test
    fun parse_invalidKeystrokeChar_isInvalidUserPhrase() {
        // "ZZ" contains chars outside the valid EZ root set.
        val json = """{"schema":1,"applicationId":"${BackupArchive.APPLICATION_ID}","userPhrases":[{"character":"x","keystroke":"ZZ"}],"dictionaryFrequencies":[]}"""
        val result = BackupArchive.parse(gzip(json), validChars)
        assertEquals(BackupArchive.ParseResult.Error.InvalidUserPhrase(0), result)
    }

    @Test
    fun parse_negativeFrequency_isInvalidUserPhrase() {
        val json = """{"schema":1,"applicationId":"${BackupArchive.APPLICATION_ID}","userPhrases":[{"character":"x","keystroke":"a","frequency":-1}],"dictionaryFrequencies":[]}"""
        val result = BackupArchive.parse(gzip(json), validChars)
        assertEquals(BackupArchive.ParseResult.Error.InvalidUserPhrase(0), result)
    }

    @Test
    fun parse_emptyCharacter_isInvalidUserPhrase() {
        val json = """{"schema":1,"applicationId":"${BackupArchive.APPLICATION_ID}","userPhrases":[{"character":"","keystroke":"a"}],"dictionaryFrequencies":[]}"""
        val result = BackupArchive.parse(gzip(json), validChars)
        assertEquals(BackupArchive.ParseResult.Error.InvalidUserPhrase(0), result)
    }

    @Test
    fun parse_controlCharInPhrase_isInvalidUserPhrase() {
        // Control char (U+0007 BEL) should fail the phrase validator even
        // though the keystroke is fine.
        val json = """{"schema":1,"applicationId":"${BackupArchive.APPLICATION_ID}","userPhrases":[{"character":"x","keystroke":"a"}],"dictionaryFrequencies":[]}"""
        val result = BackupArchive.parse(gzip(json), validChars)
        assertEquals(BackupArchive.ParseResult.Error.InvalidUserPhrase(0), result)
    }

    @Test
    fun parse_validRow_returnsOk_sanityCheck() {
        // Counterpart to the negative tests above: prove that a plain ASCII
        // letter really is accepted, so the InvalidUserPhrase failures
        // aren't false positives from an over-eager validator.
        val json =
            """{"schema":1,"applicationId":"${BackupArchive.APPLICATION_ID}","userPhrases":[{"character":"x","keystroke":"a"}],"dictionaryFrequencies":[]}"""
        val result = BackupArchive.parse(gzip(json), validChars)
        require(result is BackupArchive.ParseResult.Ok) { "expected Ok, got $result" }
        assertEquals(1, result.archive.userPhrases.size)
        assertEquals("x", result.archive.userPhrases[0].character)
    }

    @Test
    fun parse_zeroDictionaryFrequency_isInvalidFrequencyRow() {
        // Dictionary frequencies must be positive — a 0 row is meaningless
        // (the backup explicitly skips them on export) and is treated as a
        // crafted-file signal.
        val json = """{"schema":1,"applicationId":"${BackupArchive.APPLICATION_ID}","userPhrases":[],"dictionaryFrequencies":[{"character":"x","keystroke":"a","frequency":0}]}"""
        val result = BackupArchive.parse(gzip(json), validChars)
        assertEquals(BackupArchive.ParseResult.Error.InvalidFrequencyRow(0), result)
    }

    @Test
    fun parse_notGzip_isInvalidFile() {
        val plain = ByteArrayInputStream("not gzip at all".toByteArray())
        assertEquals(
            BackupArchive.ParseResult.Error.InvalidFile,
            BackupArchive.parse(plain, validChars),
        )
    }

    @Test
    fun parse_gzipButNotJson_isInvalidFile() {
        val result = BackupArchive.parse(gzip("this is not json"), validChars)
        assertEquals(BackupArchive.ParseResult.Error.InvalidFile, result)
    }

    @Test
    fun parse_oversizedUncompressedPayload_isInvalidFile() {
        // Build a string that decompresses past MAX_UNCOMPRESSED_BYTES (50 MiB).
        // Highly compressible input (zeroes) keeps the compressed test fixture
        // small — that's the same shape a "gzip bomb" takes.
        val cap = BackupArchive.MAX_UNCOMPRESSED_BYTES
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gz ->
            val buf = ByteArray(64 * 1024) // 64 KiB of NULs
            var written = 0L
            while (written <= cap + buf.size) {
                gz.write(buf)
                written += buf.size
            }
        }
        val result = BackupArchive.parse(
            ByteArrayInputStream(out.toByteArray()),
            validChars,
        )
        assertEquals(BackupArchive.ParseResult.Error.InvalidFile, result)
    }

    @Test
    fun parse_extraUnknownTopLevel_evenWithValidRows_failsStrictDecode() {
        // Sanity: a well-formed archive plus a single unknown field is still
        // rejected (we don't silently keep good rows).
        val json = """{"schema":1,"applicationId":"${BackupArchive.APPLICATION_ID}","userPhrases":[{"character":"x","keystroke":"a"}],"dictionaryFrequencies":[],"future":true}"""
        val result = BackupArchive.parse(gzip(json), validChars)
        assertEquals(BackupArchive.ParseResult.Error.UnknownField("future"), result)
    }
}
