package org.ghostsinthelab.app.rakurakuime.ui.keyboard

object SymbolLayout {
    val PUNCTUATION = listOf(
        listOf("。", "，", "、", "；", "：", "？", "！", "…"),
        listOf("「", "」", "『", "』", "（", "）", "〔", "〕"),
        listOf("【", "】", "《", "》", "〈", "〉", "～", "—")
    )

    val MATH = listOf(
        listOf("+", "-", "×", "÷", "=", "≠", "≈", "≡"),
        listOf("<", ">", "≤", "≥", "±", "∞", "∝", "√"),
        listOf("∫", "∬", "∂", "∇", "∑", "∏", "°", "π")
    )

    val ARROWS = listOf(
        listOf("↑", "↓", "←", "→", "↖", "↗", "↙", "↘"),
        listOf("↔", "↕", "⇒", "⇔", "⇑", "⇓", "⇐", "⇨"),
        listOf("▲", "▼", "◄", "►", "△", "▽", "◁", "▷")
    )

    val EMOTICONS = listOf(
        listOf("(^_^)", "(T_T)", "(@_@)", "(-_-)"),
        listOf("(^o^)", "(;_;)", "(>_<)", "m(_ _)m"),
        listOf("(´･ω･`)", "(≧▽≦)", "(ToT)", "(*^*)")
    )
    
    val CATEGORIES = listOf(
        "Punctuation" to PUNCTUATION,
        "Math" to MATH,
        "Arrows" to ARROWS,
        "Emoticons" to EMOTICONS
    )
}
