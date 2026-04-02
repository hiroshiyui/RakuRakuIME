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
    val alternates: List<String> = emptyList()
)

object KeyboardLayout {
    val KEY_HEIGHT = 48.dp

    val ROWS = listOf(
        // Row 1: Numbers & Symbols
        listOf(
            KeyDefinition("`", "厂", listOf("~")),
            KeyDefinition("1", "〡", listOf("！")),
            KeyDefinition("2", "車", listOf("＠")),
            KeyDefinition("3", "糸", listOf("＃")),
            KeyDefinition("4", "言", listOf("＄")),
            KeyDefinition("5", "貝", listOf("％")),
            KeyDefinition("6", "雨", listOf("︿")),
            KeyDefinition("7", "ㄇ", listOf("＆")),
            KeyDefinition("8", "八", listOf("＊")),
            KeyDefinition("9", "耳", listOf("（")),
            KeyDefinition("0", "鳥", listOf("）")),
            KeyDefinition("-", "儿", listOf("_", "—")),
            KeyDefinition("=", "母", listOf("+"))
        ),
        // Row 2: QWERTY
        listOf(
            KeyDefinition("q", "手"),
            KeyDefinition("w", "田"),
            KeyDefinition("e", "水", listOf("é", "è", "ê", "ë", "ē")),
            KeyDefinition("r", "口"),
            KeyDefinition("t", "廾"),
            KeyDefinition("y", "、"),
            KeyDefinition("u", "山", listOf("ú", "ù", "û", "ü", "ū")),
            KeyDefinition("i", "戈", listOf("í", "ì", "î", "ï", "ī")),
            KeyDefinition("o", "人", listOf("ó", "ò", "ô", "ö", "ō")),
            KeyDefinition("p", "心"),
            KeyDefinition("[", "匚", listOf("{")),
            KeyDefinition("]", "】", listOf("}"))
        ),
        // Row 3: ASDF
        listOf(
            KeyDefinition("a", "日", listOf("á", "à", "â", "ä", "ā")),
            KeyDefinition("s", "尸", listOf("ß")),
            KeyDefinition("d", "木"),
            KeyDefinition("f", "火"),
            KeyDefinition("g", "土"),
            KeyDefinition("h", "竹"),
            KeyDefinition("j", "十"),
            KeyDefinition("k", "大"),
            KeyDefinition("l", "中"),
            KeyDefinition(";", "寸", listOf(":", "；", "：")),
            KeyDefinition("'", "Ｌ", listOf("\"", "’", "”"))
        ),
        // Row 4: ZXCV
        listOf(
            KeyDefinition("z", "辶"),
            KeyDefinition("x", "又"),
            KeyDefinition("c", "金", listOf("ç")),
            KeyDefinition("v", "女"),
            KeyDefinition("b", "月"),
            KeyDefinition("n", "弓", listOf("ñ")),
            KeyDefinition("m", "一"),
            KeyDefinition(",", "，", listOf("，", "、", "<")),
            KeyDefinition(".", "＼", listOf("。", "．", "…", ">")),
            KeyDefinition("/", "ㄙ", listOf("？", "?")),
            KeyDefinition("\\", "的", listOf("|"))
        )
    )
}
