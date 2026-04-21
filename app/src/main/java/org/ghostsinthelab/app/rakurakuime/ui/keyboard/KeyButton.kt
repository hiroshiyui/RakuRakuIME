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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlin.math.abs
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import org.ghostsinthelab.app.rakurakuime.R
import org.ghostsinthelab.app.rakurakuime.ui.theme.KeyboardTheme
import org.ghostsinthelab.app.rakurakuime.ui.theme.RobotoSlab
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Maps a key's qwertyChar (or symbolic label like "⌫") to a pre-rendered
// keycap vector drawable imported from EzIM_Tables_Project. The drawables
// already include both the EZ root glyph and the Latin/symbol label, so when
// one is returned we draw it in place of the text labels.
@DrawableRes
private fun keycapDrawableFor(label: String): Int = when (label) {
    "`" -> R.drawable.keycode_grave
    "1" -> R.drawable.keycode_1
    "2" -> R.drawable.keycode_2
    "3" -> R.drawable.keycode_3
    "4" -> R.drawable.keycode_4
    "5" -> R.drawable.keycode_5
    "6" -> R.drawable.keycode_6
    "7" -> R.drawable.keycode_7
    "8" -> R.drawable.keycode_8
    "9" -> R.drawable.keycode_9
    "0" -> R.drawable.keycode_0
    "-" -> R.drawable.keycode_minus
    "=" -> R.drawable.keycode_equals
    "q", "Q" -> R.drawable.keycode_q
    "w", "W" -> R.drawable.keycode_w
    "e", "E" -> R.drawable.keycode_e
    "r", "R" -> R.drawable.keycode_r
    "t", "T" -> R.drawable.keycode_t
    "y", "Y" -> R.drawable.keycode_y
    "u", "U" -> R.drawable.keycode_u
    "i", "I" -> R.drawable.keycode_i
    "o", "O" -> R.drawable.keycode_o
    "p", "P" -> R.drawable.keycode_p
    "[" -> R.drawable.keycode_left_bracket
    "]" -> R.drawable.keycode_right_bracket
    "a", "A" -> R.drawable.keycode_a
    "s", "S" -> R.drawable.keycode_s
    "d", "D" -> R.drawable.keycode_d
    "f", "F" -> R.drawable.keycode_f
    "g", "G" -> R.drawable.keycode_g
    "h", "H" -> R.drawable.keycode_h
    "j", "J" -> R.drawable.keycode_j
    "k", "K" -> R.drawable.keycode_k
    "l", "L" -> R.drawable.keycode_l
    ";" -> R.drawable.keycode_semicolon
    "'" -> R.drawable.keycode_apostrophe
    "z", "Z" -> R.drawable.keycode_z
    "x", "X" -> R.drawable.keycode_x
    "c", "C" -> R.drawable.keycode_c
    "v", "V" -> R.drawable.keycode_v
    "b", "B" -> R.drawable.keycode_b
    "n", "N" -> R.drawable.keycode_n
    "m", "M" -> R.drawable.keycode_m
    "," -> R.drawable.keycode_comma
    "." -> R.drawable.keycode_period
    "/" -> R.drawable.keycode_slash
    "\\" -> R.drawable.keycode_backslash
    " " -> R.drawable.keycode_space
    "⌫" -> R.drawable.keycode_del
    "⏎" -> R.drawable.keycode_enter
    "⇧", "⇪" -> R.drawable.keycode_shift_left
    else -> 0
}

@Composable
fun KeyButton(
    keyDef: KeyDefinition,
    isUppercase: Boolean = true,
    modifier: Modifier = Modifier,
    keyHeight: androidx.compose.ui.unit.Dp = KeyboardLayout.KEY_HEIGHT,
    backgroundColorOverride: androidx.compose.ui.graphics.Color? = null,
    textColorOverride: androidx.compose.ui.graphics.Color? = null,
    // Where to pin the ezRoot label inside the key. Defaults to the
    // top-start corner (where Chinese roots sit on an EZ key); override
    // with e.g. Alignment.BottomCenter for word-labels like "Space".
    rootLabelAlignment: Alignment = Alignment.TopStart,
    // TalkBack announcement. When null, falls back to the visible
    // label: the EZ root if present, else the main character glyph.
    // Pass an explicit value for symbolic keys (⇧, ⌫, ⏎, 🌐) where
    // reading the glyph literally would not help a screen-reader user.
    contentDescription: String? = null,
    // When true, render a pre-rendered vector keycap (keycode_* drawable) in
    // place of the text labels if one is available. EZ layout opts in; other
    // layouts (English, Numbers, Emoji, function row) stay text-based.
    useKeycapDrawable: Boolean = false,
    // Explicit drawable override for the key face. When non-zero, always
    // rendered instead of text or the keycode_* auto-lookup. Useful for
    // function keys that want a glyph outside the EZ-keycap set (e.g. the
    // Material "language" icon on the Switch-IME key).
    @DrawableRes keycapDrawableRes: Int = 0,
    onSwipeUp: (() -> Unit)? = null,
    onAlternateSelected: ((String) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnAlternateSelected by rememberUpdatedState(onAlternateSelected)
    var isPressed by remember { mutableStateOf(false) }
    var showAlternates by remember { mutableStateOf(false) }
    var activeAlternateIndex by remember { mutableStateOf(-1) }
    val colors = KeyboardTheme.current
    val backgroundColor = backgroundColorOverride ?: if (isPressed && !showAlternates) colors.keyPressedBackground else colors.keyBackground
    val textColor = textColorOverride ?: colors.keyTextColor

    val displayLabel = if (isUppercase) keyDef.qwertyChar.uppercase() else keyDef.qwertyChar.lowercase()

    // TalkBack announcement: prefer the caller-provided description;
    // otherwise fall back to the EZ root, or the displayed glyph.
    val resolvedContentDescription = contentDescription
        ?: keyDef.ezRoot.ifEmpty { displayLabel }

    val density = LocalDensity.current
    val popupOffsetY = with(density) { -(keyHeight + 12.dp).roundToPx() }
    val swipeThresholdPx = with(density) { 24.dp.toPx() }

    Box(
        modifier = modifier
            .height(keyHeight)
            .widthIn(min = 28.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            // Replace all child semantics with a single Button node so
            // TalkBack issues one announcement per key instead of reading
            // every inner Text separately.
            .clearAndSetSemantics {
                this.contentDescription = resolvedContentDescription
                role = Role.Button
            }
            .background(backgroundColor)
            .pointerInput(Unit) {
                // Defer the key action until a clean release — so scroll gestures,
                // finger-drag-away, and pointer cancellations all skip the commit.
                // Mirrors MeaninglessKeyboard commit 9499e314 ("Defer key action to
                // finger-up to prevent accidental input while scrolling"), adapted
                // to keep the swipe-up and long-press-alternates behaviors.
                coroutineScope {
                    val scope = this
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        showAlternates = false
                        activeAlternateIndex = -1
                        val startY = down.position.y
                        val startX = down.position.x
                        var gestureHandled = false

                        val longPressJob = scope.launch {
                            kotlinx.coroutines.delay(400)
                            if (isPressed && !gestureHandled && keyDef.alternates.isNotEmpty()) {
                                showAlternates = true
                                activeAlternateIndex = 0
                            }
                        }

                        var released = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull() ?: break

                                // A scrollable ancestor claimed the gesture — abandon
                                // this press without committing (equivalent to
                                // tryAwaitRelease returning false in detectTapGestures).
                                if (change.isConsumed) {
                                    gestureHandled = true
                                    isPressed = false
                                    longPressJob.cancel()
                                    break
                                }

                                if (change.changedToUp()) {
                                    released = true
                                    break
                                }

                                if (showAlternates) {
                                    val dx = change.position.x - startX
                                    val itemWidthPx = with(density) { 40.dp.toPx() }
                                    val steps = (dx / itemWidthPx).toInt()
                                    activeAlternateIndex = (steps).coerceIn(0, keyDef.alternates.lastIndex)
                                } else {
                                    val dy = change.position.y - startY
                                    val dx = change.position.x - startX
                                    if (!gestureHandled && dy < -swipeThresholdPx) {
                                        // Swipe up → commit uppercase.
                                        gestureHandled = true
                                        isPressed = false
                                        longPressJob.cancel()
                                        currentOnSwipeUp?.invoke()
                                        // Consume remaining until up.
                                        while (true) {
                                            val ev = awaitPointerEvent(PointerEventPass.Main)
                                            if (ev.changes.all { it.changedToUp() }) break
                                        }
                                        released = true
                                        break
                                    } else if (!gestureHandled &&
                                        (dy > swipeThresholdPx || abs(dx) > swipeThresholdPx)) {
                                        // Finger dragged out of the key (scrolling or
                                        // sweeping away) — cancel the press silently.
                                        gestureHandled = true
                                        isPressed = false
                                        longPressJob.cancel()
                                        break
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            released = false
                        }

                        longPressJob.cancel()
                        isPressed = false

                        val wasShowingAlternates = showAlternates
                        val finalActiveIndex = activeAlternateIndex
                        showAlternates = false

                        // Fire the action ONLY on a confirmed, uncancelled release.
                        if (released && !gestureHandled) {
                            if (wasShowingAlternates && finalActiveIndex in keyDef.alternates.indices) {
                                currentOnAlternateSelected?.invoke(keyDef.alternates[finalActiveIndex])
                            } else {
                                currentOnClick()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Prefer a pre-rendered vector keycap when we have one for this key;
        // it already contains the EZ root glyph + Latin/symbol label. The key
        // falls through to the text path below for anything unmapped (🌐, EN,
        // ?123, 😀, 中, etc.).
        val keycapDrawable = when {
            keycapDrawableRes != 0 -> keycapDrawableRes
            useKeycapDrawable -> keycapDrawableFor(keyDef.qwertyChar).takeIf { it != 0 }
                ?: keycapDrawableFor(displayLabel).takeIf { it != 0 }
                ?: 0
            else -> 0
        }
        val useDrawable = keycapDrawable != 0

        if (useDrawable) {
            Image(
                painter = painterResource(id = keycapDrawable),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(textColor),
                modifier = Modifier.fillMaxSize(),
            )
        }

        // EZ Root (or free-form word) label. Positioned by rootLabelAlignment;
        // padding flips to match so it hugs the chosen edge without squeezing
        // the central qwertyChar.
        if (!useDrawable && keyDef.ezRoot.isNotEmpty()) {
            val rootPadding = when (rootLabelAlignment) {
                Alignment.BottomCenter -> Modifier.padding(bottom = 4.dp)
                Alignment.TopCenter -> Modifier.padding(top = 4.dp)
                Alignment.Center -> Modifier
                else -> Modifier.padding(start = 5.dp, top = 2.dp)
            }
            val rootIsAscii = keyDef.ezRoot.all { it.code in 0x20..0x7E }
            Text(
                text = keyDef.ezRoot,
                fontSize = 15.sp,
                fontFamily = if (rootIsAscii) RobotoSlab else null,
                fontWeight = FontWeight.Medium,
                color = colors.rootLabelColor,
                modifier = Modifier
                    .align(rootLabelAlignment)
                    .then(rootPadding),
            )
        }
        
        // Main character label (qwertyChar). Smaller when it plays second fiddle
        // to an EZ root in the same key; larger when it is the only label.
        //
        // Roboto Slab is applied when every character in the label is in the
        // printable-ASCII range — that covers single letters/digits/punctuation
        // AND multi-char word labels like "EN", "?123", "Space". CJK roots,
        // emojis, and glyph-symbols like ⇧ / ⌫ / ⏎ fall back to the system
        // default so their coverage is preserved.
        val hasEzRoot = keyDef.ezRoot.isNotEmpty()
        val useSlabFont = displayLabel.isNotEmpty() && displayLabel.all { it.code in 0x20..0x7E }
        if (!useDrawable) Text(
            text = displayLabel,
            fontSize = if (hasEzRoot) 12.sp else 20.sp,
            fontFamily = if (useSlabFont) RobotoSlab else null,
            fontWeight = if (hasEzRoot) FontWeight.Normal else FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = if (hasEzRoot) {
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = 2.dp)
            } else {
                Modifier.align(Alignment.Center)
            },
        )

        // Popup preview when pressed
        if (isPressed && !showAlternates) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, popupOffsetY),
            ) {
                Box(
                    modifier = Modifier
                        .height(60.dp)
                        .widthIn(min = 50.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.keyBackground)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (useDrawable) {
                        Image(
                            painter = painterResource(id = keycapDrawable),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(colors.keyTextColor),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        val previewText = if (keyDef.ezRoot.isNotEmpty()) keyDef.ezRoot else displayLabel
                        val previewSlab = previewText.isNotEmpty() && previewText.all { it.code in 0x20..0x7E }
                        // Multi-char labels (e.g. "?123", "EN", "中") stay
                        // on one line at a smaller size so the preview box
                        // can grow horizontally via widthIn without wrapping.
                        val previewFontSize = if (previewText.length > 1) 22.sp else 32.sp
                        Text(
                            text = previewText,
                            fontSize = previewFontSize,
                            fontFamily = if (previewSlab) RobotoSlab else null,
                            fontWeight = FontWeight.Bold,
                            color = colors.keyTextColor,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
        
        // Alternates popup
        if (showAlternates) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, popupOffsetY),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .height(60.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.keyBackground)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    keyDef.alternates.forEachIndexed { index, alt ->
                        val isSelected = index == activeAlternateIndex
                        Box(
                            modifier = Modifier
                                .size(40.dp, 50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.keyPressedBackground else androidx.compose.ui.graphics.Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            val altText = if (isUppercase) alt.uppercase() else alt.lowercase()
                            val altSlab = altText.isNotEmpty() && altText.all { it.code in 0x20..0x7E }
                            Text(
                                text = altText,
                                fontSize = 24.sp,
                                fontFamily = if (altSlab) RobotoSlab else null,
                                fontWeight = FontWeight.Bold,
                                color = colors.keyTextColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
