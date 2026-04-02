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

package org.ghostsinthelab.app.rakurakuime.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class KeyboardThemeColors(
    val keyboardBackground: Color,
    val keyBackground: Color,
    val keyPressedBackground: Color,
    val keyTextColor: Color,
    val functionKeyBackground: Color,
    val functionKeyTextColor: Color,
    val candidateBarBackground: Color,
    val candidateTextColor: Color,
    val rootLabelColor: Color,
)

val LightKeyboardColors = KeyboardThemeColors(
    keyboardBackground = Color(0xFFD2D5DB),
    keyBackground = Color(0xFFFFFFFF),
    keyPressedBackground = Color(0xFFD1D1D1),
    keyTextColor = Color(0xFF000000),
    functionKeyBackground = Color(0xFFABB1BA),
    functionKeyTextColor = Color(0xFF000000),
    candidateBarBackground = Color(0xFFF0F1F2),
    candidateTextColor = Color(0xFF000000),
    rootLabelColor = Color(0xFF8E8E93),
)

val DarkKeyboardColors = KeyboardThemeColors(
    keyboardBackground = Color(0xFF1C1C1E),
    keyBackground = Color(0xFF3A3A3C),
    keyPressedBackground = Color(0xFF2C2C2E),
    keyTextColor = Color(0xFFFFFFFF),
    functionKeyBackground = Color(0xFF2C2C2E),
    functionKeyTextColor = Color(0xFFFFFFFF),
    candidateBarBackground = Color(0xFF000000),
    candidateTextColor = Color(0xFFFFFFFF),
    rootLabelColor = Color(0xFFAEAEB2),
)

internal val LocalKeyboardThemeColors = staticCompositionLocalOf { LightKeyboardColors }

object KeyboardTheme {
    val current: KeyboardThemeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKeyboardThemeColors.current
}

@Composable
fun KeyboardTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkKeyboardColors else LightKeyboardColors
    CompositionLocalProvider(LocalKeyboardThemeColors provides colors) {
        content()
    }
}
