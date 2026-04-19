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

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { DYNAMIC, SOLARIZED }

val DynamicColorAvailable: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private val SolarizedLightColors = lightColorScheme(
    primary = SolarizedBlue,
    onPrimary = SolarizedBase3,
    primaryContainer = Color(0xFFBAD9EE),
    onPrimaryContainer = SolarizedBase03,
    secondary = SolarizedCyan,
    // Dark text on mid-tone cyan — cream read at ~2.9:1 and failed AA.
    onSecondary = SolarizedBase03,
    secondaryContainer = Color(0xFFBCE0DC),
    onSecondaryContainer = SolarizedBase03,
    tertiary = SolarizedViolet,
    onTertiary = SolarizedBase3,
    tertiaryContainer = Color(0xFFD8D8F0),
    onTertiaryContainer = SolarizedBase03,
    error = SolarizedRed,
    // Solarized red sits in the mid-luminance band where neither cream
    // nor base03 hits AA; pure white is the only Solarized-adjacent
    // choice that clears 4.5:1.
    onError = Color.White,
    errorContainer = Color(0xFFF3C6C4),
    onErrorContainer = SolarizedBase03,
    background = SolarizedBase3,
    onBackground = SolarizedBase00,
    surface = SolarizedBase3,
    onSurface = SolarizedBase00,
    surfaceVariant = SolarizedBase2,
    onSurfaceVariant = SolarizedBase01,
    // One base darker than base1 so outline strokes clear M3's 3:1 UI
    // contrast minimum against surface.
    outline = SolarizedBase01,
    outlineVariant = Color(0xFFE0DBC6),
    surfaceContainerLowest = SolarizedBase3,
    surfaceContainerLow = Color(0xFFF9F2DF),
    surfaceContainer = Color(0xFFF5EFDC),
    surfaceContainerHigh = Color(0xFFF1EBD8),
    surfaceContainerHighest = SolarizedBase2,
    inverseSurface = SolarizedBase03,
    inverseOnSurface = SolarizedBase2,
    inversePrimary = Color(0xFF84B8DE),
)

private val SolarizedDarkColors = darkColorScheme(
    primary = SolarizedBlue,
    onPrimary = SolarizedBase03,
    primaryContainer = Color(0xFF124B6D),
    onPrimaryContainer = SolarizedBase2,
    secondary = SolarizedCyan,
    onSecondary = SolarizedBase03,
    secondaryContainer = Color(0xFF12584F),
    onSecondaryContainer = SolarizedBase2,
    tertiary = SolarizedViolet,
    // Solarized violet is mid-tone; white reads clearest on it.
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF2E325F),
    onTertiaryContainer = SolarizedBase2,
    error = SolarizedRed,
    // Same reasoning as light: pure white is the only Solarized-adjacent
    // option that clears AA on mid-luminance red.
    onError = Color.White,
    errorContainer = Color(0xFF6F1916),
    onErrorContainer = SolarizedBase2,
    background = SolarizedBase03,
    onBackground = SolarizedBase0,
    surface = SolarizedBase03,
    onSurface = SolarizedBase0,
    surfaceVariant = SolarizedBase02,
    onSurfaceVariant = SolarizedBase1,
    // One base lighter than base01 so outline strokes clear 3:1 against
    // the dark surface.
    outline = SolarizedBase0,
    outlineVariant = Color(0xFF143842),
    surfaceContainerLowest = SolarizedBase03,
    surfaceContainerLow = Color(0xFF022E39),
    surfaceContainer = Color(0xFF03303C),
    surfaceContainerHigh = Color(0xFF05333F),
    surfaceContainerHighest = SolarizedBase02,
    inverseSurface = SolarizedBase2,
    inverseOnSurface = SolarizedBase01,
    inversePrimary = Color(0xFF0E5B88),
)

@Composable
fun RakuRakuIMETheme(
    themeMode: ThemeMode = ThemeMode.DYNAMIC,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        themeMode == ThemeMode.DYNAMIC && DynamicColorAvailable -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> SolarizedDarkColors
        else -> SolarizedLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
