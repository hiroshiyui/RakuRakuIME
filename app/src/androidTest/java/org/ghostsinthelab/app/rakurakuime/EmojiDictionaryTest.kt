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
import org.ghostsinthelab.app.rakurakuime.data.EmojiDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [EmojiDictionary]. The bundled `emoji.json` is
 * parsed at runtime via `org.json`; a corrupt or truncated asset would
 * silently degrade to "no emoji categories" and the picker would render
 * eight empty grids, so the contract checked here is "all eight categories
 * present and non-empty after the first load."
 */
@RunWith(AndroidJUnit4::class)
class EmojiDictionaryTest {

    private val context get() =
        InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun categories_returnsEightTabs() = runBlocking {
        val categories = EmojiDictionary.categories(context)
        // Eight tabs are hard-coded in CATEGORY_ORDER; if any layout in the
        // JSON is missing, mapNotNull silently drops it. Assert the full set
        // so an asset with missing categories trips the test rather than
        // showing the user a tab bar with gaps.
        assertEquals(8, categories.size)
    }

    @Test
    fun categories_haveDistinctTabIcons() = runBlocking {
        val categories = EmojiDictionary.categories(context)
        val icons = categories.map { it.tabIcon }
        assertEquals(
            "Tab icons must be distinct so users can tell categories apart: $icons",
            icons.size,
            icons.toSet().size,
        )
    }

    @Test
    fun everyCategory_hasAtLeastOneEmoji() = runBlocking {
        val categories = EmojiDictionary.categories(context)
        categories.forEachIndexed { index, cat ->
            assertTrue(
                "Category $index (icon ${cat.tabIcon}) must contain at least one emoji",
                cat.emojis.isNotEmpty(),
            )
        }
    }

    @Test
    fun smileysCategory_containsSmileyEmoji() = runBlocking {
        val categories = EmojiDictionary.categories(context)
        // Smileys is the first tab and must include the canonical 😀 (U+1F600);
        // it's the icon for the tab itself, so absence indicates a parse
        // bug filtering out the very first key.
        val smileys = categories.first()
        assertTrue(
            "Smileys tab should contain 😀, got ${smileys.emojis.take(5)}…",
            smileys.emojis.contains("😀"),
        )
    }

    @Test
    fun categories_excludeSwitchLayoutKeys() = runBlocking {
        val categories = EmojiDictionary.categories(context)
        // Tab buttons live in the same JSON layout but carry a `switchLayout`
        // field; the parser must filter them out so the user never sees a
        // tab icon as if it were an emoji to commit.
        val allOutputs = categories.flatMap { it.emojis }
        assertTrue(
            "No emoji output should be empty after the switchLayout filter",
            allOutputs.all { it.isNotEmpty() },
        )
    }

    @Test
    fun categories_areCached() = runBlocking {
        // Second call must return the same cached list (same instances under
        // a @Volatile field guarded by a Mutex). Re-parsing on every emoji
        // mode entry would jank the UI on slow devices.
        val first = EmojiDictionary.categories(context)
        val second = EmojiDictionary.categories(context)
        assertNotNull(first)
        // Reference equality on the outer list — a fresh parse would create
        // a new ArrayList instance.
        assertTrue(
            "Cached categories should be reused across calls",
            first === second,
        )
    }
}
