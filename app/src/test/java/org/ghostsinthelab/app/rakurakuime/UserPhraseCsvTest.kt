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

import org.ghostsinthelab.app.rakurakuime.data.UserPhraseCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPhraseCsvTest {
    // Subset sufficient for the test fixtures used below.
    private val validChars = ('a'..'z').toSet() + ('0'..'9').toSet() + setOf(',', '.', '/', ';')

    @Test
    fun exportThenParse_roundTripsAllRows() {
        val rows = listOf(
            UserPhraseCsv.Row("輕鬆", "2mm/"),
            UserPhraseCsv.Row("信件", "o4oj"),
            UserPhraseCsv.Row("a,b", "a"),       // comma in field
            UserPhraseCsv.Row("she said \"hi\"", "a"), // quote in field
        )
        val csv = UserPhraseCsv.export(rows)
        val parsed = UserPhraseCsv.parse(csv.lines(), validChars)
        assertTrue(parsed is UserPhraseCsv.ParseResult.Ok)
        assertEquals(rows, (parsed as UserPhraseCsv.ParseResult.Ok).rows)
    }

    @Test
    fun parse_rejectsBadHeader() {
        val csv = listOf("\"phrase\",\"keystroke\"", "\"a\",\"a\"")
        val result = UserPhraseCsv.parse(csv, validChars)
        assertEquals(UserPhraseCsv.ParseResult.Error.InvalidHeader, result)
    }

    @Test
    fun parse_rejectsEmpty() {
        val result = UserPhraseCsv.parse(emptyList(), validChars)
        assertEquals(UserPhraseCsv.ParseResult.Error.InvalidFile, result)
    }

    @Test
    fun parse_rejectsRowWithInvalidKeystroke() {
        val csv = listOf(UserPhraseCsv.HEADER, "\"abc\",\"ABC\"")
        val result = UserPhraseCsv.parse(csv, validChars)
        assertTrue(result is UserPhraseCsv.ParseResult.Error.InvalidKeystroke)
        assertEquals(2, (result as UserPhraseCsv.ParseResult.Error.InvalidKeystroke).lineNumber)
    }

    @Test
    fun parse_rejectsEmptyField() {
        val csv = listOf(UserPhraseCsv.HEADER, "\"\",\"a\"")
        val result = UserPhraseCsv.parse(csv, validChars)
        assertTrue(result is UserPhraseCsv.ParseResult.Error.MalformedRow)
    }

    @Test
    fun isValidKeystroke_emptyIsInvalid() {
        assertEquals(false, UserPhraseCsv.isValidKeystroke("", validChars))
    }

    @Test
    fun isValidKeystroke_unknownCharIsInvalid() {
        assertEquals(false, UserPhraseCsv.isValidKeystroke("abZ", validChars))
    }

    @Test
    fun isValidKeystroke_allValidCharsPasses() {
        assertEquals(true, UserPhraseCsv.isValidKeystroke("a1,.", validChars))
    }

    @Test
    fun parse_skipsBlankLines() {
        val csv = listOf(UserPhraseCsv.HEADER, "", "\"a\",\"a\"", "", "\"b\",\"b\"")
        val result = UserPhraseCsv.parse(csv, validChars)
        assertTrue(result is UserPhraseCsv.ParseResult.Ok)
        assertEquals(2, (result as UserPhraseCsv.ParseResult.Ok).rows.size)
    }
}
