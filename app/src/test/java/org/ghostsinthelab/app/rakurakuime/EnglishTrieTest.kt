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

import org.ghostsinthelab.app.rakurakuime.data.EnglishTrie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-side tests for [EnglishTrie]. The trie backs the English-mode
 * prediction strip — `assets/google_10k_english.txt` is loaded line-by-line
 * (frequency-ordered), so the contract that DFS yields candidates in
 * insertion order is load-bearing.
 */
class EnglishTrieTest {

    @Test
    fun emptyTrie_returnsEmptyForAnyPrefix() {
        val trie = EnglishTrie()
        assertEquals(emptyList<String>(), trie.prefixLookup(""))
        assertEquals(emptyList<String>(), trie.prefixLookup("a"))
        assertEquals(emptyList<String>(), trie.prefixLookup("hello"))
    }

    @Test
    fun unmatchedPrefix_returnsEmpty() {
        val trie = EnglishTrie().apply {
            insert("apple")
            insert("application")
        }
        assertEquals(emptyList<String>(), trie.prefixLookup("b"))
        assertEquals(emptyList<String>(), trie.prefixLookup("ax"))
    }

    @Test
    fun exactWord_isReturnedAsItsOwnPrefixMatch() {
        val trie = EnglishTrie().apply { insert("hello") }
        assertEquals(listOf("hello"), trie.prefixLookup("hello"))
    }

    @Test
    fun prefixMatch_listsAllDescendants() {
        val trie = EnglishTrie().apply {
            insert("car")
            insert("card")
            insert("cards")
        }
        assertEquals(listOf("car", "card", "cards"), trie.prefixLookup("car"))
    }

    @Test
    fun preservesInsertionOrder_so_frequencyRankingSurvives() {
        // Google 10K corpus is sorted by descending frequency. When sibling
        // children are added to the same node, the trie must surface
        // earlier-inserted ones first; flipping to alphabetical would
        // silently degrade prediction quality.
        //
        // Picked terminals at the same depth with no further descendants so
        // the test isolates sibling ordering from preorder-DFS interleaving.
        val trie = EnglishTrie().apply {
            insert("zb")  // most frequent
            insert("za")
            insert("zd")
            insert("zc")
        }
        assertEquals(
            listOf("zb", "za", "zd", "zc"),
            trie.prefixLookup("z"),
        )
    }

    @Test
    fun preservesInsertionOrder_deepFanout() {
        // Children are stored in a LinkedHashMap; the deeper "ap-" branch
        // must follow the same first-inserted-first-out rule across levels.
        val trie = EnglishTrie().apply {
            insert("apple")
            insert("apply")
            insert("apt")
            insert("ant")
        }
        assertEquals(listOf("apple", "apply", "apt"), trie.prefixLookup("ap"))
        assertEquals(listOf("apple", "apply", "apt", "ant"), trie.prefixLookup("a"))
    }

    @Test
    fun limit_capsResults() {
        // Words are leaf-only siblings of the same parent so the order is
        // pure insertion order — keeps the test focused on the limit cap.
        val trie = EnglishTrie().apply {
            "abcdefghij".forEach { insert("z$it") }
        }
        val results = trie.prefixLookup("z", limit = 5)
        assertEquals(5, results.size)
        assertEquals(listOf("za", "zb", "zc", "zd", "ze"), results)
    }

    @Test
    fun limit_zero_returnsEmpty() {
        val trie = EnglishTrie().apply { insert("a") }
        assertTrue(trie.prefixLookup("a", limit = 0).isEmpty())
    }

    @Test
    fun emptyPrefix_walksWholeTrie() {
        val trie = EnglishTrie().apply {
            insert("a")
            insert("b")
        }
        assertEquals(listOf("a", "b"), trie.prefixLookup(""))
    }

    @Test
    fun reinsertSameWord_doesNotDuplicate() {
        val trie = EnglishTrie().apply {
            insert("the")
            insert("the")
        }
        assertEquals(listOf("the"), trie.prefixLookup("the"))
    }

    @Test
    fun prefixMatch_excludesUnrelatedSiblings() {
        val trie = EnglishTrie().apply {
            insert("car")
            insert("cat")
            insert("dog")
        }
        assertEquals(listOf("car"), trie.prefixLookup("car"))
        assertEquals(listOf("car", "cat"), trie.prefixLookup("ca"))
    }
}
