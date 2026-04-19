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

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.ghostsinthelab.app.rakurakuime.R

/**
 * Vendored Roboto Slab family (Google Fonts, SIL Open Font License 1.1).
 * Used for alphanumerical keycap glyphs so letters and digits wear a slab
 * serif that reads clearly at small key sizes. CJK, emoji, and symbol
 * glyphs fall back to the system default because Roboto Slab's cmap does
 * not cover them.
 */
val RobotoSlab: FontFamily = FontFamily(
    Font(R.font.roboto_slab_regular, FontWeight.Normal),
    Font(R.font.roboto_slab_medium, FontWeight.Medium),
    Font(R.font.roboto_slab_bold, FontWeight.Bold),
)
