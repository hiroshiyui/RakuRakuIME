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

        assertEquals(listOf("輕鬆"), dao.getCharacters("hujqkho"))
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
}
