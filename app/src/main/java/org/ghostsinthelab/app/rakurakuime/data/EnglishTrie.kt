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

private class EnglishTrieNode {
    // LinkedHashMap so prefix-DFS yields candidates in insertion (frequency) order.
    val children: LinkedHashMap<Char, EnglishTrieNode> = LinkedHashMap()
    var terminalWord: String? = null
}

class EnglishTrie {
    private val root = EnglishTrieNode()

    fun insert(word: String) {
        var node = root
        for (ch in word) {
            node = node.children.getOrPut(ch) { EnglishTrieNode() }
        }
        node.terminalWord = word
    }

    fun prefixLookup(prefix: String, limit: Int = 20): List<String> {
        var node = root
        for (ch in prefix) {
            node = node.children[ch] ?: return emptyList()
        }
        val results = ArrayList<String>(limit)
        collectTerminals(node, results, limit)
        return results
    }

    private fun collectTerminals(node: EnglishTrieNode, out: MutableList<String>, limit: Int) {
        if (out.size >= limit) return
        node.terminalWord?.let { out.add(it) }
        for (child in node.children.values) {
            if (out.size >= limit) return
            collectTerminals(child, out, limit)
        }
    }
}
