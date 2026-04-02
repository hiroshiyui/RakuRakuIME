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

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        // Mode toggle
        val modeKeyDef = when (inputMode) {
            InputMode.EZ -> KeyDefinition("?123")
            InputMode.NUMBER -> KeyDefinition("=\\<+")
            InputMode.SYMBOL -> KeyDefinition("ABC")
        }
        KeyButton(
            keyDef = modeKeyDef,
            modifier = Modifier.weight(1.5f),
            keyHeight = keyHeight,
            backgroundColorOverride = colors.functionKeyBackground,
            textColorOverride = colors.functionKeyTextColor,
            onClick = {
                when (inputMode) {
                    InputMode.EZ -> onToggleMode(InputMode.NUMBER)
                    InputMode.NUMBER -> onToggleMode(InputMode.SYMBOL)
                    InputMode.SYMBOL -> onToggleMode(InputMode.EZ)
                }
            }
        )

        // Switch IME (Globe)
        KeyButton(
            keyDef = KeyDefinition("🌐"),
            modifier = Modifier.weight(1f),
            keyHeight = keyHeight,
            backgroundColorOverride = colors.functionKeyBackground,
            textColorOverride = colors.functionKeyTextColor,
            onClick = onSwitchIme
        )

        if (shouldSplit) {
            Spacer(modifier = Modifier.weight(1f))
        }

        // Space
        KeyButton(
            keyDef = KeyDefinition(" ", "Space"),
            modifier = Modifier.weight(4f),
            keyHeight = keyHeight,
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
            onClick = onBackspace
        )

        // Enter
        KeyButton(
            keyDef = KeyDefinition("⏎"),
            modifier = Modifier.weight(1.5f),
            keyHeight = keyHeight,
            backgroundColorOverride = colors.functionKeyBackground,
            textColorOverride = colors.functionKeyTextColor,
            onClick = onEnter
        )
    }
}
