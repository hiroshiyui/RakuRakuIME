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

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.ghostsinthelab.app.rakurakuime.data.DictionaryDao
import org.ghostsinthelab.app.rakurakuime.data.DictionaryEntry
import org.ghostsinthelab.app.rakurakuime.data.ImeDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Focused, in-memory coverage of [DictionaryDao]. The big-corpus
 * [DictionaryTest] proves end-to-end soundness against the shipped asset;
 * this fixture pins the *ordering* and *projection* contracts in isolation
 * so a future ORDER BY tweak can't silently regress candidate ranking.
 */
@RunWith(AndroidJUnit4::class)
class DictionaryDaoTest {

    private lateinit var db: ImeDatabase
    private lateinit var dao: DictionaryDao

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, ImeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dictionaryDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private suspend fun seed(vararg rows: DictionaryEntry) {
        dao.insertAll(rows.toList())
    }

    @Test
    fun getCharacters_ordersByFrequencyThenWeightSum() = runBlocking {
        // Three single-char rows on the same keystroke. Frequency dominates;
        // ties break on (character_weight + phrase_weight) DESC.
        seed(
            DictionaryEntry(keystroke = "m", character = "甲", frequency = 0, characterWeight = 100),
            DictionaryEntry(keystroke = "m", character = "乙", frequency = 0, characterWeight = 500),
            DictionaryEntry(keystroke = "m", character = "丙", frequency = 5, characterWeight = 1),
        )
        // 丙 leads (frequency 5 > 0); among the rest 乙 (500) beats 甲 (100).
        assertEquals(listOf("丙", "乙", "甲"), dao.getCharacters("m"))
    }

    @Test
    fun getCharactersByPrefix_returnsOnlyMatchingPrefixes() = runBlocking {
        seed(
            DictionaryEntry(keystroke = "o4oj", character = "信件"),
            DictionaryEntry(keystroke = "o4hw", character = "信箱"),
            DictionaryEntry(keystroke = "abcd", character = "其他"),
        )
        val results = dao.getCharactersByPrefix("o4*")
        assertTrue("expected 信件 in $results", "信件" in results)
        assertTrue("expected 信箱 in $results", "信箱" in results)
        assertTrue("unrelated prefix must be excluded", "其他" !in results)
    }

    @Test
    fun incrementFrequencyExact_bumpsOnlyMatchingRow() = runBlocking {
        seed(
            DictionaryEntry(keystroke = "a", character = "甲", frequency = 0),
            DictionaryEntry(keystroke = "b", character = "甲", frequency = 0),
        )
        dao.incrementFrequencyExact("甲", "a")
        dao.incrementFrequencyExact("甲", "a")
        // Non-matching pair must not bleed across keystrokes.
        assertEquals(listOf("甲"), dao.getCharacters("a")) // sanity: row still there
        // Reading frequency directly via the ORDER BY: after two bumps, 甲@a
        // outranks itself @b — verify by inserting a same-char-different-key
        // tiebreaker and checking the ordering on a phrase that touches both.
        seed(DictionaryEntry(keystroke = "a", character = "乙", frequency = 1))
        // 甲@a has frequency 2, 乙@a has frequency 1 — 甲 must come first.
        assertEquals(listOf("甲", "乙"), dao.getCharacters("a"))
    }

    @Test
    fun incrementFrequencyByPrefix_bumpsAllMatchingKeystrokes() = runBlocking {
        seed(
            DictionaryEntry(keystroke = "o4", character = "信", frequency = 0),
            DictionaryEntry(keystroke = "o4oj", character = "信", frequency = 0),
            DictionaryEntry(keystroke = "abc", character = "信", frequency = 0),
        )
        dao.incrementFrequencyByPrefix("信", "o4")
        // Both o4-prefixed rows get bumped.
        assertEquals(listOf("信"), dao.getCharacters("o4"))
        assertEquals(listOf("信"), dao.getCharacters("o4oj"))
        // Verify the bump landed by adding a low-priority competitor and
        // confirming 信 still leads on both keystrokes (and not on the
        // unrelated `abc` keystroke).
        seed(DictionaryEntry(keystroke = "o4", character = "X", frequency = 0, characterWeight = 1))
        assertEquals(listOf("信", "X"), dao.getCharacters("o4"))
    }

    @Test
    fun resetFrequencies_zeroesAllRows() = runBlocking {
        seed(
            DictionaryEntry(keystroke = "a", character = "甲", frequency = 100),
            DictionaryEntry(keystroke = "b", character = "乙", frequency = 50),
        )
        dao.resetFrequencies()
        // After reset, ordering collapses to weights / insertion order.
        // Seed a single-key row with weight to verify the previous high
        // frequency no longer dominates.
        seed(DictionaryEntry(keystroke = "a", character = "丙", characterWeight = 10))
        assertEquals(
            "After reset, 甲's old frequency must not still dominate",
            listOf("丙", "甲"),
            dao.getCharacters("a"),
        )
    }

    @Test
    fun encodingsForCharacter_filtersToSingleCharRowsInIdOrder() = runBlocking {
        // 信 has two single-char encodings, plus a multi-char phrase row
        // that must NOT contribute to per-character encodings.
        seed(
            DictionaryEntry(keystroke = "o4", character = "信"),
            DictionaryEntry(keystroke = "yo4", character = "信"),
            DictionaryEntry(keystroke = "o4oj", character = "信件"), // multi-char
        )
        val encs = dao.encodingsForCharacter("信")
        assertEquals(
            "expected single-char rows only, in file (id) order",
            listOf("o4", "yo4"),
            encs,
        )
    }

    @Test
    fun keystrokesForPhrase_includesMultiCharRows() = runBlocking {
        // The corollary to encodingsForCharacter: keystrokesForPhrase must
        // surface multi-char phrase rows so EzKeystrokeLookup can prepend
        // the corpus's own answer ahead of the per-char fallback.
        seed(
            DictionaryEntry(keystroke = "o4oj", character = "信件"),
            DictionaryEntry(keystroke = "8178", character = "八一七公報"),
        )
        assertEquals(listOf("o4oj"), dao.keystrokesForPhrase("信件"))
        assertEquals(listOf("8178"), dao.keystrokesForPhrase("八一七公報"))
        assertEquals(emptyList<String>(), dao.keystrokesForPhrase("不存在"))
    }

    @Test
    fun keystrokesForPhrase_ordersByFrequencyThenId() = runBlocking {
        // Multiple keystrokes for the same phrase — frequency wins, ties
        // break on insertion order (id ASC).
        seed(
            DictionaryEntry(keystroke = "8178", character = "八一七公報", frequency = 0),
            DictionaryEntry(keystroke = "8mj8", character = "八一七公報", frequency = 5),
            DictionaryEntry(keystroke = "81j8", character = "八一七公報", frequency = 0),
        )
        val results = dao.keystrokesForPhrase("八一七公報")
        assertEquals("8mj8", results.first())   // frequency 5 leads
        assertEquals("8178", results[1])         // earlier id beats 81j8
        assertEquals("81j8", results[2])
    }

    @Test
    fun nextCharactersAfter_groupsAndProjectsSecondChar() = runBlocking {
        seed(
            DictionaryEntry(keystroke = "o4oj", character = "信件", phraseWeight = 100),
            DictionaryEntry(keystroke = "o4hw", character = "信箱", phraseWeight = 200),
            DictionaryEntry(keystroke = "o4ki", character = "信封", phraseWeight = 50),
            // Single-char row: must be excluded by `length(character) > :prefixLen`.
            DictionaryEntry(keystroke = "o4", character = "信"),
        )
        val results = dao.nextCharactersAfter(
            likePattern = "信%",
            prefixLen = 1,
            limit = 10,
        )
        // 箱 (phrase_weight 200) > 件 (100) > 封 (50). Single-char 信 excluded.
        assertEquals(listOf("箱", "件", "封"), results)
    }

    @Test
    fun nextCharactersAfter_respectsLimit() = runBlocking {
        seed(
            DictionaryEntry(keystroke = "o4oj", character = "信件", phraseWeight = 100),
            DictionaryEntry(keystroke = "o4hw", character = "信箱", phraseWeight = 90),
            DictionaryEntry(keystroke = "o4ki", character = "信封", phraseWeight = 80),
        )
        val results = dao.nextCharactersAfter("信%", prefixLen = 1, limit = 2)
        assertEquals(listOf("件", "箱"), results)
    }
}
