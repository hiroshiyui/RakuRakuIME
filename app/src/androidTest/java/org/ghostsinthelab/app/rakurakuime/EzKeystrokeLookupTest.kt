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
import org.ghostsinthelab.app.rakurakuime.data.CinParser
import org.ghostsinthelab.app.rakurakuime.data.EzKeystrokeLookup
import org.ghostsinthelab.app.rakurakuime.data.ImeDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EzKeystrokeLookupTest {

    private lateinit var db: ImeDatabase

    @Before
    fun setup() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = ImeDatabase.getDatabase(ctx)
        if (db.dictionaryDao().count() == 0) {
            CinParser.parseAndPopulate(ctx, db)
        }
    }

    @Test
    fun suggest_twoCharPhrase_concatenatesFullEncodings() = runBlocking {
        // 2-char rule: each char contributes its full chosen encoding.
        // 信件 = 信(o4) + 件(oj) = "o4oj".
        val results = EzKeystrokeLookup.suggest("信件", db.dictionaryDao())
        assertTrue("expected suggestions, got empty", results.isNotEmpty())
        assertEquals("o4oj", results.first())
    }

    @Test
    fun suggest_twoCharPhrase_singleKeyChar() = runBlocking {
        // 一 has both `16` (all-digit, 速成) and `m` (single-key).
        // Picking rule: single-key wins, so 一's chosen encoding is `m`,
        // not `mm` (single-key 頭 and 尾 collapse to one key).
        // 一月 → m + b = "mb".
        val results = EzKeystrokeLookup.suggest("一月", db.dictionaryDao())
        assertEquals("mb", results.first())
    }

    @Test
    fun suggest_threeCharPhrase_headsOnly() = runBlocking {
        // 3-char rule: each char contributes ONLY its 頭 (first key of
        // the chosen encoding). 智多星 corpus encoding is `,,a`:
        //   智(`,a` → `,`) + 多(`,,` → `,`) + 星(`ag` → `a`).
        val results = EzKeystrokeLookup.suggest("智多星", db.dictionaryDao())
        assertEquals(",,a", results.first())
    }

    @Test
    fun suggest_threeCharPhrase_surfacesAlternateWhenAmbiguous() = runBlocking {
        // 失智症: 失(`,k`) + 智(`,a`) + 症(`m` first in file, `ym` later).
        // Canonical picking-rule winner: ",,`" (using ` as 症's head).
        // Alternate: ",,y" (using ym).
        val results = EzKeystrokeLookup.suggest("失智症", db.dictionaryDao())
        assertEquals(",,`", results.first())
        assertTrue(
            "expected ,,y as alternate, got $results",
            results.contains(",,y"),
        )
    }

    @Test
    fun suggest_fiveCharPhrase_usesFirstFourHeadsOnly() = runBlocking {
        // 5+ char rule (per-char fallback): take heads of first 4 chars only.
        // 八一七公報 (from EzIM_Tables_Project CLAUDE.md example):
        //   八(`8`) + 一(`m`) + 七(skip `76` digit-only, take `j'` → `j`)
        //     + 公(`8/` → `8`) = "8mj8". 報 is ignored.
        //
        // The corpus *also* has 八一七公報 as a phrase row with several
        // hand-tuned encodings, so the phrase-level lookup surfaces those
        // ahead of the per-char rule. Either way, the per-char answer must
        // still appear in the list as an alternate.
        val results = EzKeystrokeLookup.suggest("八一七公報", db.dictionaryDao())
        assertTrue(
            "expected the per-char answer 8mj8 in $results",
            results.contains("8mj8"),
        )
    }

    @Test
    fun suggest_singleChar_returnsFullEncoding() = runBlocking {
        // 1-char rule: the full chosen encoding. 失 = `,k`.
        val results = EzKeystrokeLookup.suggest("失", db.dictionaryDao())
        assertEquals(",k", results.first())
    }

    @Test
    fun suggest_emptyPhrase_returnsEmpty() = runBlocking {
        assertEquals(emptyList<String>(), EzKeystrokeLookup.suggest("", db.dictionaryDao()))
    }

    @Test
    fun suggest_unknownCharBailsOut() = runBlocking {
        // A truly novel phrase: no exact corpus row (so the phrase-level
        // path returns nothing) AND a char that has no single-char EZ
        // row (so the per-char fallback bails out). Latin letters turn
        // out to *be* in the corpus (the MOE 字頻 dataset slipped some
        // through), so we pick a Cyrillic char that's unambiguously
        // absent from a Chinese IME's tables. Contract: drop to empty
        // so the caller falls back to manual entry.
        val results = EzKeystrokeLookup.suggest("信Ы", db.dictionaryDao())
        assertEquals(emptyList<String>(), results)
    }

    @Test
    fun suggest_respectsTotalLimit() = runBlocking {
        // 失智症 has 2 alternates; capping to 1 must return just the canonical.
        val results = EzKeystrokeLookup.suggest("失智症", db.dictionaryDao(), totalLimit = 1)
        assertEquals(listOf(",,`"), results)
    }

    @Test
    fun keystrokesForPhrase_returnsCorpusEncodingForKnownPhrase() = runBlocking {
        // 信件 is stored in the bundled corpus as o4oj (full per-char concat).
        // Unlike encodingsForCharacter, keystrokesForPhrase does not filter
        // to single-char rows, so this multi-char row must surface.
        val keys = db.dictionaryDao().keystrokesForPhrase("信件")
        assertTrue("expected o4oj for 信件, got $keys", keys.contains("o4oj"))
    }

    @Test
    fun keystrokesForPhrase_emptyForUnknownPhrase() = runBlocking {
        // A truly novel phrase (latin + Chinese) shouldn't exist in the
        // corpus; the phrase-level path returns empty and the per-char
        // fallback takes over.
        assertEquals(
            emptyList<String>(),
            db.dictionaryDao().keystrokesForPhrase("信XYZQ"),
        )
    }

    @Test
    fun suggest_phraseLevelKeystrokeLeads() = runBlocking {
        // For a phrase already in the corpus, the corpus encoding must be
        // surfaced first — the per-char fallback only runs after the
        // phrase-level lookup. Both paths happen to yield the same answer
        // for 信件 (o4oj), so the assertion below is on the first result:
        // dedup means the corpus row contributes that string before the
        // DFS gets to it.
        val results = EzKeystrokeLookup.suggest("信件", db.dictionaryDao())
        assertEquals("o4oj", results.first())
    }
}
