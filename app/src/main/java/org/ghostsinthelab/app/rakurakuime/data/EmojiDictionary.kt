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
import org.json.JSONObject

/**
 * Emoji categories loaded from the bundled `emoji.json` asset.
 * The JSON schema follows MeaninglessKeyboard's `KeyboardPack` format:
 * layouts → rows → keys, where each terminal key has `label`/`output`;
 * keys that carry a `switchLayout` field are tab buttons and are filtered out.
 */
object EmojiDictionary {
    private const val ASSET_NAME = "emoji.json"

    data class Category(val tabIcon: String, val emojis: List<String>)

    // Canonical category order; mirrors MeaninglessKeyboard's tab bar.
    private val CATEGORY_ORDER = listOf(
        "smileys" to "😀",
        "gestures" to "👋",
        "animals" to "🐱",
        "food" to "🍕",
        "travel" to "🚗",
        "activities" to "⚽",
        "objects" to "💡",
        "symbols" to "🏁",
    )

    @Volatile
    private var cached: List<Category>? = null
    private val loadMutex = Mutex()

    suspend fun categories(context: Context): List<Category> {
        cached?.let { return it }
        return loadMutex.withLock {
            cached ?: withContext(Dispatchers.IO) { parse(context) }.also { cached = it }
        }
    }

    private fun parse(context: Context): List<Category> {
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val layouts = root.getJSONObject("layouts")
        return CATEGORY_ORDER.mapNotNull { (name, icon) ->
            val layout = layouts.optJSONObject(name) ?: return@mapNotNull null
            val rows = layout.getJSONArray("rows")
            val emojis = ArrayList<String>()
            for (i in 0 until rows.length()) {
                val keys = rows.getJSONObject(i).getJSONArray("keys")
                for (j in 0 until keys.length()) {
                    val key = keys.getJSONObject(j)
                    if (key.has("switchLayout")) continue
                    key.optString("output").takeIf { it.isNotEmpty() }?.let(emojis::add)
                }
            }
            Category(tabIcon = icon, emojis = emojis)
        }
    }
}
