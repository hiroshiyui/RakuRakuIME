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

import org.ghostsinthelab.app.rakurakuime.data.FrequencyCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM coverage for [FrequencyCsv.parseLines]. The asset-based loader is
 * tested end-to-end via the production seed (the prebuilt asset DB is
 * built by [`buildImeDb`] from these CSVs); this unit test pins the
 * reciprocal weight formula and the header / cutoff / dup-key edges.
 */
class FrequencyCsvTest {

    @Test
    fun parseLines_appliesReciprocalWeight() {
        // floor(10000 / rank): exact integer values at known ranks.
        val lines = sequenceOf(
            "序號,字頻", // header — skipped
            "1,的",
            "2,一",
            "10,人",
            "100,後",
            "1000,深",
        )
        val out = FrequencyCsv.parseLines(lines)
        assertEquals(10_000, out["的"])
        assertEquals(5_000, out["一"])
        assertEquals(1_000, out["人"])
        assertEquals(100, out["後"])
        assertEquals(10, out["深"])
    }

    @Test
    fun parseLines_stopsAtFirstZeroWeight() {
        // Once rank > SEED_NUMERATOR (10 000) the weight floors to 0 and
        // parseLines must short-circuit — every subsequent row would also
        // contribute zero in a properly sorted corpus.
        val lines = sequenceOf(
            "序號,字頻",
            "9999,A",  // weight = 1
            "10001,B", // weight = 0 → break
            "5,C",     // not reached even though weight would be 2000
        )
        val out = FrequencyCsv.parseLines(lines)
        assertEquals(1, out["A"])
        assertFalse("B is past the cutoff and must not be emitted", out.containsKey("B"))
        assertNull("Parser must short-circuit on zero weight, never reaching C", out["C"])
    }

    @Test
    fun parseLines_skipsHeaderOnlyOnce() {
        // The first non-numeric row is consumed as the header. Subsequent
        // non-numeric rows are stray data and silently dropped — not
        // mistaken for headers.
        val lines = sequenceOf(
            "序號,字頻",        // header
            "stray,data",       // stray non-numeric row
            "1,的",
        )
        val out = FrequencyCsv.parseLines(lines)
        assertEquals(mapOf("的" to 10_000), out)
    }

    @Test
    fun parseLines_skipsBlankAndShortRows() {
        val lines = sequenceOf(
            "序號,字頻",
            "",                 // blank
            "   ",              // whitespace-only
            "1",                // missing key column
            "1,",               // empty key
            "2,人",
        )
        val out = FrequencyCsv.parseLines(lines)
        assertEquals(mapOf("人" to 5_000), out)
    }

    @Test
    fun parseLines_dupKey_keepsLowestRank() {
        // The corpus shouldn't ship duplicates, but the contract is
        // documented: highest weight (= lowest rank) wins so a stray
        // duplicate can't downgrade a high-rank entry.
        val lines = sequenceOf(
            "序號,字頻",
            "100,的",   // weight 100
            "1,的",     // weight 10 000 — wins
            "5000,的",  // weight 2 — must not overwrite
        )
        val out = FrequencyCsv.parseLines(lines)
        assertEquals(10_000, out["的"])
    }

    @Test
    fun parseLines_trimsWhitespaceAroundColumns() {
        // The CSV is comma-split without quote handling, so values can
        // arrive with leading / trailing whitespace from the source file.
        val lines = sequenceOf(
            "序號,字頻",
            "  1 ,  的  ",
        )
        val out = FrequencyCsv.parseLines(lines)
        assertEquals(mapOf("的" to 10_000), out)
    }

    @Test
    fun parseLines_emptyInput_returnsEmptyMap() {
        assertEquals(emptyMap<String, Int>(), FrequencyCsv.parseLines(emptySequence()))
    }

    @Test
    fun parseLines_zeroRank_treatedAsZeroWeight() {
        // Defensive: rank 0 would mean division by zero. The implementation
        // floors to 0 and the loop breaks at the first row.
        val lines = sequenceOf(
            "序號,字頻",
            "0,X",
            "1,的", // never reached due to the break above
        )
        val out = FrequencyCsv.parseLines(lines)
        assertEquals(emptyMap<String, Int>(), out)
    }
}
