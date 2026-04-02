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
