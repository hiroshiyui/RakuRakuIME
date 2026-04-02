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

import androidx.compose.ui.unit.dp

data class KeyDefinition(
    val qwertyChar: String,
    val ezRoot: String = "",
)

object KeyboardLayout {
    val KEY_HEIGHT = 48.dp

    val ROWS = listOf(
        // Row 1: Numbers & Symbols
        listOf(
            KeyDefinition("`", "厂"),
            KeyDefinition("1", "〡"),
            KeyDefinition("2", "車"),
            KeyDefinition("3", "糸"),
            KeyDefinition("4", "言"),
            KeyDefinition("5", "貝"),
            KeyDefinition("6", "雨"),
            KeyDefinition("7", "ㄇ"),
            KeyDefinition("8", "八"),
            KeyDefinition("9", "耳"),
            KeyDefinition("0", "鳥"),
            KeyDefinition("-", "儿"),
            KeyDefinition("=", "母")
        ),
        // Row 2: QWERTY
        listOf(
            KeyDefinition("q", "手"),
            KeyDefinition("w", "田"),
            KeyDefinition("e", "水"),
            KeyDefinition("r", "口"),
            KeyDefinition("t", "廾"),
            KeyDefinition("y", "、"),
            KeyDefinition("u", "山"),
            KeyDefinition("i", "戈"),
            KeyDefinition("o", "人"),
            KeyDefinition("p", "心"),
            KeyDefinition("[", "匚"),
            KeyDefinition("]", "】")
        ),
        // Row 3: ASDF
        listOf(
            KeyDefinition("a", "日"),
            KeyDefinition("s", "尸"),
            KeyDefinition("d", "木"),
            KeyDefinition("f", "火"),
            KeyDefinition("g", "土"),
            KeyDefinition("h", "竹"),
            KeyDefinition("j", "十"),
            KeyDefinition("k", "大"),
            KeyDefinition("l", "中"),
            KeyDefinition(";", "寸"),
            KeyDefinition("'", "Ｌ")
        ),
        // Row 4: ZXCV
        listOf(
            KeyDefinition("z", "辶"),
            KeyDefinition("x", "又"),
            KeyDefinition("c", "金"),
            KeyDefinition("v", "女"),
            KeyDefinition("b", "月"),
            KeyDefinition("n", "弓"),
            KeyDefinition("m", "一"),
            KeyDefinition(",", "，"),
            KeyDefinition(".", "＼"),
            KeyDefinition("/", "ㄙ"),
            KeyDefinition("\\", "的")
        )
    )
}
