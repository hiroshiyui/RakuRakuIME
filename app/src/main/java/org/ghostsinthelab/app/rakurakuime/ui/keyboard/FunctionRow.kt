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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import org.ghostsinthelab.app.rakurakuime.R
import org.ghostsinthelab.app.rakurakuime.ui.InputMode
import org.ghostsinthelab.app.rakurakuime.ui.theme.KeyboardTheme

@Composable
fun FunctionRow(
    inputMode: InputMode,
    isShifted: Boolean,
    keyHeight: androidx.compose.ui.unit.Dp = KeyboardLayout.KEY_HEIGHT,
    shouldSplit: Boolean = false,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onToggleShift: () -> Unit,
    onToggleMode: (InputMode) -> Unit,
    onSwitchIme: () -> Unit,
) {
    val colors = KeyboardTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Mode cycle: EZ -> ENGLISH -> NUMBER -> EMOJI -> EZ.
        // The key label previews the *next* mode in the cycle.
        val modeKeyDef = when (inputMode) {
            InputMode.EZ -> KeyDefinition("EN")
            InputMode.ENGLISH -> KeyDefinition("?123")
            InputMode.NUMBER -> KeyDefinition("\uD83D\uDE00") // 😀
            InputMode.EMOJI -> KeyDefinition("輕")
        }
        val modeKeyDescription = when (inputMode) {
            InputMode.EZ -> stringResource(R.string.a11y_key_mode_to_english)
            InputMode.ENGLISH -> stringResource(R.string.a11y_key_mode_to_numbers)
            InputMode.NUMBER -> stringResource(R.string.a11y_key_mode_to_emoji)
            InputMode.EMOJI -> stringResource(R.string.a11y_key_mode_to_chinese)
        }
        var showModeMenu by remember { mutableStateOf(false) }
        val stickyPopups = LocalStickyPopups.current
        val density = LocalDensity.current
        // Float the menu just above the function row, mirroring the offset
        // used by KeyButton's preview / alternates popups.
        val modeMenuOffsetY = with(density) { -(keyHeight + 12.dp).roundToPx() }

        Box(modifier = Modifier.weight(1.5f)) {
            KeyButton(
                keyDef = modeKeyDef,
                modifier = Modifier.fillMaxWidth(),
                keyHeight = keyHeight,
                backgroundColorOverride = colors.functionKeyBackground,
                textColorOverride = colors.functionKeyTextColor,
                contentDescription = modeKeyDescription,
                onClick = {
                    val next = when (inputMode) {
                        InputMode.EZ -> InputMode.ENGLISH
                        InputMode.ENGLISH -> InputMode.NUMBER
                        InputMode.NUMBER -> InputMode.EMOJI
                        InputMode.EMOJI -> InputMode.EZ
                    }
                    onToggleMode(next)
                },
                onLongPress = {
                    stickyPopups.openExclusive { showModeMenu = false }
                    showModeMenu = true
                }
            )

            if (showModeMenu) {
                // Sticky picker — stays visible until the user taps an entry.
                // Non-focusable Popup keeps text-input focus on the editor;
                // touches inside the popup bounds still reach the menu items.
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, modeMenuOffsetY),
                ) {
                    Column(
                        modifier = Modifier
                            .shadow(8.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.keyBackground)
                            .padding(vertical = 4.dp)
                    ) {
                        val entries = listOf(
                            InputMode.EZ to stringResource(R.string.mode_picker_ez),
                            InputMode.ENGLISH to stringResource(R.string.mode_picker_english),
                            InputMode.NUMBER to stringResource(R.string.mode_picker_numbers),
                            InputMode.EMOJI to stringResource(R.string.mode_picker_emoji),
                        )
                        entries.forEach { (mode, label) ->
                            val isCurrent = mode == inputMode
                            Box(
                                modifier = Modifier
                                    .clickable {
                                        stickyPopups.dismiss()
                                        if (!isCurrent) onToggleMode(mode)
                                    }
                                    .background(
                                        if (isCurrent) colors.keyPressedBackground
                                        else androidx.compose.ui.graphics.Color.Transparent
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 16.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = colors.keyTextColor,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Switch IME (Globe)
        KeyButton(
            keyDef = KeyDefinition(""),
            modifier = Modifier.weight(1f),
            keyHeight = keyHeight,
            backgroundColorOverride = colors.functionKeyBackground,
            textColorOverride = colors.functionKeyTextColor,
            contentDescription = stringResource(R.string.a11y_key_switch_ime),
            keycapDrawableRes = R.drawable.outline_language_24,
            onClick = onSwitchIme
        )

        if (shouldSplit) {
            Spacer(modifier = Modifier.weight(1f))
        }

        // Space — label is U+2423 OPEN BOX.
        KeyButton(
            keyDef = KeyDefinition(" ", "\u2423"),
            modifier = Modifier.weight(4f),
            keyHeight = keyHeight,
            rootLabelAlignment = Alignment.BottomCenter,
            contentDescription = stringResource(R.string.a11y_key_space),
            onClick = onSpace
        )

        if (shouldSplit) {
            Spacer(modifier = Modifier.weight(1f))
        }

        // Backspace
        KeyButton(
            keyDef = KeyDefinition("⌫"),
            modifier = Modifier.weight(1.5f),
            keyHeight = keyHeight,
            backgroundColorOverride = colors.functionKeyBackground,
            textColorOverride = colors.functionKeyTextColor,
            contentDescription = stringResource(R.string.a11y_key_backspace),
            onClick = onBackspace
        )

        // Enter
        KeyButton(
            keyDef = KeyDefinition("⏎"),
            modifier = Modifier.weight(1.5f),
            keyHeight = keyHeight,
            backgroundColorOverride = colors.functionKeyBackground,
            textColorOverride = colors.functionKeyTextColor,
            contentDescription = stringResource(R.string.a11y_key_enter),
            onClick = onEnter
        )
    }
}
