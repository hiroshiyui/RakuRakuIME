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

/**
 * CSV serialisation for the User Phrase Manager backup / restore flow.
 *
 * Format: two-column CSV with the literal header `"character","keystroke"`,
 * RFC-4180-style quoting (every field is always double-quoted, embedded `"`
 * is doubled). Kept in a non-Android file so it can be exercised by plain
 * JVM unit tests.
 */
object UserPhraseCsv {
    const val HEADER: String = "\"character\",\"keystroke\""
    private const val MAX_TOTAL_BYTES = 1_000_000
    private const val MAX_CHARACTER_LEN = 100
    private const val MAX_KEYSTROKE_LEN = 200

    sealed class ParseResult {
        data class Ok(val rows: List<Row>) : ParseResult()
        sealed class Error : ParseResult() {
            object InvalidFile : Error()
            object InvalidHeader : Error()
            data class MalformedRow(val lineNumber: Int) : Error()
            data class InvalidKeystroke(val lineNumber: Int) : Error()
        }
    }

    data class Row(val character: String, val keystroke: String)

    fun export(rows: List<Row>): String = buildString {
        append(HEADER).append('\n')
        for (row in rows) {
            append('"').append(escape(row.character)).append("\",\"")
                .append(escape(row.keystroke)).append("\"\n")
        }
    }

    /**
     * Parse and validate the given lines. [validKeystrokeChars] is the set of
     * legal EZ root keys (from [CinParser.validKeystrokeChars]); rows with
     * keystrokes containing anything else are rejected.
     */
    fun parse(lines: List<String>, validKeystrokeChars: Set<Char>): ParseResult {
        if (lines.isEmpty()) return ParseResult.Error.InvalidFile

        val totalLength = lines.sumOf { it.length }
        if (totalLength > MAX_TOTAL_BYTES) return ParseResult.Error.InvalidFile

        if (lines.first().trim() != HEADER) return ParseResult.Error.InvalidHeader

        val rows = mutableListOf<Row>()
        for ((index, line) in lines.withIndex()) {
            if (index == 0) continue
            if (line.isBlank()) continue
            val parsed = parseLine(line)
            val lineNo = index + 1
            if (parsed.size != 2) return ParseResult.Error.MalformedRow(lineNo)
            val character = parsed[0]
            val keystroke = parsed[1]
            if (character.isEmpty() || keystroke.isEmpty()) {
                return ParseResult.Error.MalformedRow(lineNo)
            }
            if (character.length > MAX_CHARACTER_LEN || keystroke.length > MAX_KEYSTROKE_LEN) {
                return ParseResult.Error.MalformedRow(lineNo)
            }
            if (character.any { it.isISOControl() } || keystroke.any { it.isISOControl() }) {
                return ParseResult.Error.MalformedRow(lineNo)
            }
            if (!isValidKeystroke(keystroke, validKeystrokeChars)) {
                return ParseResult.Error.InvalidKeystroke(lineNo)
            }
            rows.add(Row(character, keystroke))
        }
        return ParseResult.Ok(rows)
    }

    fun isValidKeystroke(keystroke: String, validChars: Set<Char>): Boolean {
        if (keystroke.isEmpty()) return false
        return keystroke.all { it in validChars }
    }

    private fun escape(field: String): String = field.replace("\"", "\"\"")

    private fun parseLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> {
                    fields.add(current.toString()); current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}
