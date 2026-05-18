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
import org.ghostsinthelab.app.rakurakuime.data.ImeDatabase
import org.ghostsinthelab.app.rakurakuime.data.UserPhraseEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPhraseDaoTest {

    private lateinit var db: ImeDatabase

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // In-memory DB — avoids interfering with the on-device app DB.
        db = Room.inMemoryDatabaseBuilder(ctx, ImeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertEnumerateDelete() = runBlocking {
        val dao = db.userPhraseDao()
        val id1 = dao.insert(UserPhraseEntry(character = "輕鬆", keystroke = "2mm/"))
        val id2 = dao.insert(UserPhraseEntry(character = "信件", keystroke = "o4oj"))
        assertTrue(id1 > 0L && id2 > 0L)
        assertEquals(2, dao.count())
        // Duplicate (character, keystroke) is ignored by the unique index.
        val idDup = dao.insert(UserPhraseEntry(character = "輕鬆", keystroke = "2mm/"))
        assertEquals(-1L, idDup)
        assertEquals(2, dao.count())

        val all = dao.enumerateAll()
        assertEquals(2, all.size)

        dao.deleteById(id1)
        assertEquals(1, dao.count())
        assertEquals("信件", dao.enumerateAll().first().character)
    }

    @Test
    fun getCharactersByKeystrokeAndPrefix() = runBlocking {
        val dao = db.userPhraseDao()
        dao.insert(UserPhraseEntry(character = "輕鬆", keystroke = "2mm/"))
        dao.insert(UserPhraseEntry(character = "信件", keystroke = "o4oj"))
        dao.insert(UserPhraseEntry(character = "信箱", keystroke = "o4hw"))

        assertEquals(listOf("輕鬆"), dao.getCharacters("2mm/"))
        val prefix = dao.getCharactersByPrefix("o4")
        assertTrue(prefix.containsAll(listOf("信件", "信箱")))
    }

    @Test
    fun nextCharactersAfter_minesUserPhrases() = runBlocking {
        val dao = db.userPhraseDao()
        dao.insert(UserPhraseEntry(character = "信件", keystroke = "o4oj"))
        dao.insert(UserPhraseEntry(character = "信箱", keystroke = "o4hw"))

        val next = dao.nextCharactersAfter(likePattern = "信%", prefixLen = 1, limit = 10)
        assertTrue(next.containsAll(listOf("件", "箱")))
    }

    @Test
    fun clearAll_empties() = runBlocking {
        val dao = db.userPhraseDao()
        dao.insert(UserPhraseEntry(character = "甲", keystroke = "a"))
        dao.insert(UserPhraseEntry(character = "乙", keystroke = "b"))
        dao.clearAll()
        assertEquals(0, dao.count())
    }

    @Test
    fun updateById_changesCharacterAndKeystrokeInPlace() = runBlocking {
        val dao = db.userPhraseDao()
        val id = dao.insert(UserPhraseEntry(character = "信件", keystroke = "o4oj"))

        // Bump frequency so we can verify in-place edit preserves it
        // (delete-recreate would reset the counter).
        dao.incrementFrequencyExact("信件", "o4oj")
        dao.incrementFrequencyExact("信件", "o4oj")

        val rows = dao.updateById(id, character = "信箱", keystroke = "o4hw")
        assertEquals(1, rows)

        val all = dao.enumerateAll()
        assertEquals(1, all.size)
        val row = all.first()
        assertEquals(id, row.id) // id preserved
        assertEquals("信箱", row.character)
        assertEquals("o4hw", row.keystroke)
        assertEquals(2L, row.frequency) // frequency preserved
    }

    @Test
    fun updateById_collidesWithExistingRow_returnsZero() = runBlocking {
        val dao = db.userPhraseDao()
        dao.insert(UserPhraseEntry(character = "信件", keystroke = "o4oj"))
        val id2 = dao.insert(UserPhraseEntry(character = "信箱", keystroke = "o4hw"))

        // Try to make row #2 collide with row #1 on the unique index.
        // `UPDATE OR IGNORE` should leave the table untouched.
        val rows = dao.updateById(id2, character = "信件", keystroke = "o4oj")
        assertEquals(0, rows)
        assertEquals(2, dao.count())
        // Row #2 is unchanged so the original (信箱, o4hw) still resolves.
        assertEquals(listOf("信箱"), dao.getCharacters("o4hw"))
    }

    @Test
    fun incrementFrequencyExact_bumpsOnlyMatchingRow() = runBlocking {
        val dao = db.userPhraseDao()
        dao.insert(UserPhraseEntry(character = "甲", keystroke = "a"))
        dao.insert(UserPhraseEntry(character = "乙", keystroke = "b"))

        dao.incrementFrequencyExact("甲", "a")
        dao.incrementFrequencyExact("甲", "a")
        dao.incrementFrequencyExact("甲", "a")
        // Non-matching pair is a no-op (mirrors the "bump both DAOs"
        // policy in KeyboardViewModel.selectCandidate).
        dao.incrementFrequencyExact("丙", "c")

        val rows = dao.enumerateAll().associateBy { it.character }
        assertEquals(3L, rows.getValue("甲").frequency)
        assertEquals(0L, rows.getValue("乙").frequency)
    }

    @Test
    fun getCharacters_ordersByFrequencyDescThenCreatedAt() = runBlocking {
        val dao = db.userPhraseDao()
        // Same keystroke, three different characters — the unique index is
        // on (character, keystroke), so this is allowed.
        dao.insert(UserPhraseEntry(character = "甲", keystroke = "k", createdAt = 100L))
        dao.insert(UserPhraseEntry(character = "乙", keystroke = "k", createdAt = 200L))
        dao.insert(UserPhraseEntry(character = "丙", keystroke = "k", createdAt = 300L))

        // No bumps yet → tie on frequency, newest created_at wins.
        assertEquals(listOf("丙", "乙", "甲"), dao.getCharacters("k"))

        // Bump 甲 twice → it leapfrogs both newer entries.
        dao.incrementFrequencyExact("甲", "k")
        dao.incrementFrequencyExact("甲", "k")
        assertEquals(listOf("甲", "丙", "乙"), dao.getCharacters("k"))
    }
}
