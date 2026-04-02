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

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
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

fun KeyboardThemeColors(colorScheme: ColorScheme): KeyboardThemeColors = KeyboardThemeColors(
    keyboardBackground = colorScheme.surfaceContainerLow,
    keyBackground = colorScheme.surfaceContainerHigh,
    keyPressedBackground = colorScheme.surfaceContainerHighest,
    keyTextColor = colorScheme.onSurface,
    functionKeyBackground = colorScheme.secondaryContainer,
    functionKeyTextColor = colorScheme.onSecondaryContainer,
    candidateBarBackground = colorScheme.surfaceContainer,
    candidateTextColor = colorScheme.onSurface,
    rootLabelColor = colorScheme.onSurfaceVariant,
)

internal val LocalKeyboardThemeColors = staticCompositionLocalOf { KeyboardThemeColors(lightColorScheme()) }

object KeyboardTheme {
    val current: KeyboardThemeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKeyboardThemeColors.current
}

@Composable
fun KeyboardTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val colors = remember(colorScheme) {
        KeyboardThemeColors(colorScheme)
    }
    CompositionLocalProvider(LocalKeyboardThemeColors provides colors) {
        content()
    }
}
