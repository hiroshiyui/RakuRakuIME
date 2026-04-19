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

package org.ghostsinthelab.app.rakurakuime.ui.keyboard

/**
 * Full PC-style QWERTY layout for ENGLISH mode. Every printable ASCII
 * symbol is reachable either as a primary key or as a long-press
 * alternate that mirrors the US-QWERTY shifted character for that key.
 *
 * Rows:
 *   Row 1 (13): ` 1 2 3 4 5 6 7 8 9 0 - =   alts: ~ ! @ # $ % ^ & * ( ) _ +
 *   Row 2 (12): q w e r t y u i o p [ ]     alts for bracket keys: { }
 *   Row 3 (11): a s d f g h j k l ; '       alts: : "
 *   Row 4 (11): z x c v b n m , . / \       alts: < > ? |   (shift is rendered
 *                                                            separately by
 *                                                            KeyboardScreen)
 *
 * KeyboardScreen pads shorter rows with weighted spacers so key widths
 * stay visually aligned across rows.
 */
object EnglishLayout {
    /** Total column weight each rendered row is normalised to. */
    const val ROW_WEIGHT: Float = 13f

    /** Weight of the shift key prepended to [SHIFT_ROW_INDEX]. */
    const val SHIFT_WEIGHT: Float = 1.5f

    val ROWS: List<List<KeyDefinition>> = listOf(
        listOf(
            KeyDefinition("`", "", listOf("~")),
            KeyDefinition("1", "", listOf("!")),
            KeyDefinition("2", "", listOf("@")),
            KeyDefinition("3", "", listOf("#")),
            KeyDefinition("4", "", listOf("$")),
            KeyDefinition("5", "", listOf("%")),
            KeyDefinition("6", "", listOf("^")),
            KeyDefinition("7", "", listOf("&")),
            KeyDefinition("8", "", listOf("*")),
            KeyDefinition("9", "", listOf("(")),
            KeyDefinition("0", "", listOf(")")),
            KeyDefinition("-", "", listOf("_")),
            KeyDefinition("=", "", listOf("+")),
        ),
        listOf(
            KeyDefinition("q"), KeyDefinition("w"), KeyDefinition("e"), KeyDefinition("r"),
            KeyDefinition("t"), KeyDefinition("y"), KeyDefinition("u"), KeyDefinition("i"),
            KeyDefinition("o"), KeyDefinition("p"),
            KeyDefinition("[", "", listOf("{")),
            KeyDefinition("]", "", listOf("}")),
        ),
        listOf(
            KeyDefinition("a"), KeyDefinition("s"), KeyDefinition("d"), KeyDefinition("f"),
            KeyDefinition("g"), KeyDefinition("h"), KeyDefinition("j"), KeyDefinition("k"),
            KeyDefinition("l"),
            KeyDefinition(";", "", listOf(":")),
            KeyDefinition("'", "", listOf("\"")),
        ),
        listOf(
            KeyDefinition("z"), KeyDefinition("x"), KeyDefinition("c"), KeyDefinition("v"),
            KeyDefinition("b"), KeyDefinition("n"), KeyDefinition("m"),
            KeyDefinition(",", "", listOf("<")),
            KeyDefinition(".", "", listOf(">")),
            KeyDefinition("/", "", listOf("?")),
            KeyDefinition("\\", "", listOf("|")),
        ),
    )

    /** The row that gets the shift key prepended at render time. */
    val SHIFT_ROW_INDEX: Int = ROWS.lastIndex
}
