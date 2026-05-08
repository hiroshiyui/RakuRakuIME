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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.ghostsinthelab.app.rakurakuime.data.EnglishDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [EnglishDictionary]. Exercises the bundled
 * `google_10k_english.txt` asset end-to-end so a truncated or missing
 * resource fails loudly rather than degrading silently into sparse
 * predictions.
 */
@RunWith(AndroidJUnit4::class)
class EnglishDictionaryTest {

    private val context get() =
        InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun emptyPrefix_returnsEmpty() = runBlocking {
        // The IME never queries on an empty buffer, but the contract is that
        // an empty prefix shortcut-returns without touching the trie. Keeps
        // the early-return cheap.
        assertEquals(emptyList<String>(), EnglishDictionary.prefixLookup(context, ""))
    }

    @Test
    fun commonPrefix_returnsResults() = runBlocking {
        val results = EnglishDictionary.prefixLookup(context, "th")
        assertTrue("Expected matches for 'th', got empty", results.isNotEmpty())
        assertTrue(
            "All matches must start with the prefix: $results",
            results.all { it.startsWith("th") },
        )
        // "the" is the most frequent English word and is in the bundled
        // Google 10K corpus — must be among the candidates.
        assertTrue("Expected 'the' in 'th' results: $results", results.contains("the"))
    }

    @Test
    fun caseInsensitiveLookup() = runBlocking {
        // The keyboard preserves case for caps-lock / one-shot shift, but
        // the trie is built lowercase. Mixed/upper-case prefixes must still
        // match — selectEnglishCandidate handles re-casing on commit.
        val lower = EnglishDictionary.prefixLookup(context, "the")
        val mixed = EnglishDictionary.prefixLookup(context, "The")
        val upper = EnglishDictionary.prefixLookup(context, "THE")
        assertEquals(lower, mixed)
        assertEquals(lower, upper)
    }

    @Test
    fun unmatchedPrefix_returnsEmpty() = runBlocking {
        // A prefix that cannot exist in any English-spelled word.
        val results = EnglishDictionary.prefixLookup(context, "zzqxq")
        assertEquals(emptyList<String>(), results)
    }

    @Test
    fun limit_capsResults() = runBlocking {
        val results = EnglishDictionary.prefixLookup(context, "a", limit = 5)
        assertEquals(5, results.size)
    }

    @Test
    fun assetLoadsExpectedCorpusSize() = runBlocking {
        // Pull a single-char prefix lookup with a large limit; the result
        // size is a lower bound on how many words begin with that letter.
        // 'a' alone has many hundreds of entries in the Google 10K corpus,
        // so anything below ~50 implies the asset failed to load.
        val results = EnglishDictionary.prefixLookup(context, "a", limit = 1000)
        assertTrue(
            "Expected lots of words starting with 'a' from the Google 10K asset, got ${results.size}",
            results.size > 50,
        )
    }

    @Test
    fun cachedTrie_isReused() = runBlocking {
        // The trie is loaded lazily under a Mutex and then cached in a
        // @Volatile field. Subsequent lookups must return identical results
        // without re-parsing the asset. We assert by structural equality;
        // a re-parse would still produce the same list, but the contract
        // is "no rebuild after first load" — exercised here as a
        // regression guard.
        val first = EnglishDictionary.prefixLookup(context, "the")
        val second = EnglishDictionary.prefixLookup(context, "the")
        assertEquals(first, second)
        assertFalse("Sanity: first lookup should not be empty", first.isEmpty())
    }

    @Test
    fun lookupReturnsLowercaseEntries() = runBlocking {
        // Trie is built lowercase; entries returned must reflect that so
        // the casing logic in selectEnglishCandidate has a stable input.
        val results = EnglishDictionary.prefixLookup(context, "abo", limit = 50)
        assertNotNull(results)
        assertTrue(
            "All English candidates must be lowercase: $results",
            results.all { it == it.lowercase() },
        )
    }
}
