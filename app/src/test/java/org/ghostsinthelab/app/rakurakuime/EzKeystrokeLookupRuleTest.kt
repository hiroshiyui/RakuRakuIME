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

import org.ghostsinthelab.app.rakurakuime.data.EzKeystrokeLookup
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM coverage of the per-character picking rule (the part of the
 * lookup that doesn't need a DB). End-to-end behaviour against the real
 * corpus is covered by `EzKeystrokeLookupTest` in androidTest.
 */
class EzKeystrokeLookupRuleTest {

    @Test
    fun picking_singleKeyWinsOverDigitAndMulti() {
        // 一 in EzIM_Tables_Project example: ["16", "m"] → "m" first.
        val ordered = EzKeystrokeLookup.sortByEzPickingRule(listOf("16", "m"))
        assertEquals(listOf("m", "16"), ordered)
    }

    @Test
    fun picking_skipsAllDigitWhenNoSingleKey() {
        // 七: ["76", "j'", "m'"] → "j'" first (skip all-digit "76").
        val ordered = EzKeystrokeLookup.sortByEzPickingRule(listOf("76", "j'", "m'"))
        assertEquals(listOf("j'", "m'", "76"), ordered)
    }

    @Test
    fun picking_fileOrderBreaksRemainingTies() {
        // No single-key, no all-digit — first in file wins.
        val ordered = EzKeystrokeLookup.sortByEzPickingRule(listOf("`m", "ym"))
        assertEquals(listOf("`m", "ym"), ordered)
    }

    @Test
    fun picking_passesThroughSingleton() {
        assertEquals(listOf(",k"), EzKeystrokeLookup.sortByEzPickingRule(listOf(",k")))
    }

    @Test
    fun picking_passesThroughEmpty() {
        assertEquals(emptyList<String>(), EzKeystrokeLookup.sortByEzPickingRule(emptyList()))
    }
}
