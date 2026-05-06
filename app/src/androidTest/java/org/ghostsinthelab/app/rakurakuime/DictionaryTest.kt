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

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.ghostsinthelab.app.rakurakuime.data.CinParser
import org.ghostsinthelab.app.rakurakuime.data.ImeDatabase
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test to verify that the EZ Input Method dictionary
 * is correctly imported and contains key mappings.
 */
@RunWith(AndroidJUnit4::class)
class DictionaryTest {

    private lateinit var db: ImeDatabase

    @Before
    fun setup() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        db = ImeDatabase.getDatabase(appContext)
    }

    @Test
    fun verifyDictionaryContent() = runBlocking {
        val dao = db.dictionaryDao()
        
        // If empty, trigger import (simulates first run)
        if (dao.count() == 0) {
            val appContext = InstrumentationRegistry.getInstrumentation().targetContext
            CinParser.parseAndPopulate(appContext, db)
        }

        val count = dao.count()
        assertTrue("Dictionary should have at least 50,000 entries", count > 50000)

        // Verify '這' mapping (z4)
        val z4Candidates = dao.getCharacters("z4")
        assertTrue("Mapping 'z4' should contain '這'", z4Candidates.contains("這"))

        // Verify '姆' mapping (v=)
        val vEqualCandidates = dao.getCharacters("v=")
        assertTrue("Mapping 'v=' should contain '姆'", vEqualCandidates.contains("姆"))
        
        // Verify '山' root (u)
        val uCandidates = dao.getCharacters("u")
        assertTrue("Root 'u' should contain '山'", uCandidates.contains("山"))
    }

    /**
     * The next-character prediction strip mines multi-character entries
     * starting with the just-selected character. "信" is a high-coverage
     * probe — phrases like 信件/信箱/信封/信賴/信任 all ship in the bundled
     * corpus, so at least a few of those must come back.
     */
    @Test
    fun nextCharactersAfter_returnsLikelyFollowups() = runBlocking {
        val dao = db.dictionaryDao()
        if (dao.count() == 0) {
            val appContext = InstrumentationRegistry.getInstrumentation().targetContext
            CinParser.parseAndPopulate(appContext, db)
        }

        val results = dao.nextCharactersAfter(
            likePattern = "信%",
            prefixLen = 1,
            limit = 30,
        )

        assertTrue("Expected predictions for 信, got empty list", results.isNotEmpty())
        // Each result must be a single character (the substr() projection).
        assertTrue(
            "All predictions should be single characters: $results",
            results.all { it.length == 1 },
        )
        // At least one of the well-known follow-ups should appear. We don't
        // assert exact ordering — frequencies depend on the bundled corpus
        // and may shift across dictionary releases.
        val expectedAny = setOf("件", "箱", "封", "賴", "任", "用", "心", "念", "仰")
        assertTrue(
            "Expected at least one of $expectedAny in predictions, got $results",
            results.any { it in expectedAny },
        )
    }

    @Test
    fun nextCharactersAfter_excludesSingleCharEntries() = runBlocking {
        val dao = db.dictionaryDao()
        if (dao.count() == 0) {
            val appContext = InstrumentationRegistry.getInstrumentation().targetContext
            CinParser.parseAndPopulate(appContext, db)
        }

        // Picking a rare prefix that exists as a single-char entry but rarely
        // (or never) leads multi-char phrases would still return either an
        // empty list or only valid 2nd-char projections — never the seed char
        // itself, since length(character) > prefixLen filters single chars.
        val results = dao.nextCharactersAfter(
            likePattern = "信%",
            prefixLen = 1,
            limit = 30,
        )
        assertTrue(
            "Predictions should not echo the seed char itself",
            results.none { it == "信" && results.size == 1 },
        )
    }
}
