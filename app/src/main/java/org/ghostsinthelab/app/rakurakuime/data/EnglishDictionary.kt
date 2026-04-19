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
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object EnglishDictionary {
    private const val TAG = "EnglishDictionary"
    private const val ASSET_NAME = "google_10k_english.txt"
    // The bundled Google 10K corpus contains ~9 894 words; anything much
    // lower implies a truncated or corrupt APK. Log loudly and keep going
    // with whatever we could parse (predictions degrade rather than crash).
    private const val MIN_EXPECTED_WORDS = 9_000

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
                var inserted = 0
                context.assets.open(ASSET_NAME).bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val w = line.trim()
                        if (w.isNotEmpty()) {
                            built.insert(w.lowercase())
                            inserted++
                        }
                    }
                }
                if (inserted < MIN_EXPECTED_WORDS) {
                    Log.w(
                        TAG,
                        "Loaded only $inserted words from $ASSET_NAME " +
                            "(expected at least $MIN_EXPECTED_WORDS); " +
                            "English predictions will be sparse.",
                    )
                }
                built
            }.also { trie = it }
        }
    }
}
