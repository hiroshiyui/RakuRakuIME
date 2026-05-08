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
import org.ghostsinthelab.app.rakurakuime.data.ImeDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [CinParser]. The parser's job is twofold:
 * compute a stable hash over the bundled CIN asset (so first launch can
 * trust the pre-packaged DB and later launches can detect asset
 * upgrades), and drive the actual import when the asset has changed.
 *
 * These tests are read-only against the real DB whenever possible —
 * they avoid `forceReimport` so a developer running them on a real
 * device doesn't lose learned candidate frequencies.
 */
@RunWith(AndroidJUnit4::class)
class CinParserTest {

    private val context get() =
        InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var db: ImeDatabase

    @Before
    fun setup() {
        db = ImeDatabase.getDatabase(context)
    }

    @Test
    fun assetHash_isDeterministic() = runBlocking {
        val a = CinParser.assetHash(context)
        val b = CinParser.assetHash(context)
        assertEquals("Hash must be deterministic across calls", a, b)
    }

    @Test
    fun assetHash_isSha256HexString() = runBlocking {
        val hash = CinParser.assetHash(context)
        assertEquals("SHA-256 hex output must be 64 characters", 64, hash.length)
        assertTrue(
            "SHA-256 hex output must contain only [0-9a-f]: $hash",
            hash.all { it in '0'..'9' || it in 'a'..'f' },
        )
    }

    @Test
    fun syncWithAsset_isIdempotent() = runBlocking {
        // Run sync twice; the second call must short-circuit to AlreadyCurrent
        // because the first call already aligned the stored hash with the
        // shipped asset. Without idempotency every cold launch would re-
        // import the dictionary and wipe learned frequencies.
        CinParser.syncWithAsset(context, db)
        val second = CinParser.syncWithAsset(context, db)
        assertEquals(CinParser.SyncResult.AlreadyCurrent, second)
    }

    @Test
    fun syncWithAsset_leavesPopulatedDatabase() = runBlocking {
        // Whatever path syncWithAsset takes (trust pre-packaged DB / reimport),
        // the post-condition is the same: the dictionary table is populated.
        CinParser.syncWithAsset(context, db)
        val count = db.dictionaryDao().count()
        assertTrue(
            "Dictionary should be populated after sync, got $count rows",
            count > 50_000,
        )
    }

}
