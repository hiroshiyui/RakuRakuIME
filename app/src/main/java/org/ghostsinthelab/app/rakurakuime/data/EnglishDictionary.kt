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

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object EnglishDictionary {
    private const val ASSET_NAME = "google_10k_english.txt"

    @Volatile
    private var trie: EnglishTrie? = null
    private val loadMutex = Mutex()

    suspend fun prefixLookup(context: Context, prefix: String, limit: Int = 20): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val loaded = getOrLoadTrie(context)
        return loaded.prefixLookup(prefix.lowercase(), limit)
    }

    private suspend fun getOrLoadTrie(context: Context): EnglishTrie {
        trie?.let { return it }
        return loadMutex.withLock {
            trie ?: withContext(Dispatchers.IO) {
                val built = EnglishTrie()
                context.assets.open(ASSET_NAME).bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val w = line.trim()
                        if (w.isNotEmpty()) built.insert(w.lowercase())
                    }
                }
                built
            }.also { trie = it }
        }
    }
}
